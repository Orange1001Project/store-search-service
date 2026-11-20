package com.storesearch.refresh.service;

import com.storesearch.common.text.Csv;
import com.storesearch.embedding.EmbeddingClient;
import com.storesearch.refresh.config.LoadProperties;
import com.storesearch.refresh.repository.DataVersionRepository;
import com.storesearch.refresh.source.SourceFileReader;
import com.storesearch.store.repository.SnapshotPartitionRepository;
import com.storesearch.store.service.SnapshotRow;
import com.storesearch.store.service.StoreLoadService;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 첫 적재(데이터 버전 1). 벡터는 Colab GPU에서 만들기 때문에 두 번에 나눠 실행한다(docs/dev/DATA_LOAD.md).
 *
 * <ol>
 *   <li>prepare: 원천 엑셀 → 같은 가게 합치기 → 9월 id 이어받기 → store 넣기 + 버전(building) → Colab 입력 파일
 *   <li>import: Colab 벡터 확인 → 스냅샷 파티션 채우기·인덱스 → 검증 → 한 트랜잭션으로 붙이고 버전 활성
 * </ol>
 * 주간 갱신(바뀐 가게만 임베딩 서버로)은 9단계에서 같은 부품으로 만든다.
 */
@Service
public class InitialLoadService {

	private static final Logger log = LoggerFactory.getLogger(InitialLoadService.class);

	/** 로컬 서버(CPU)와 Colab(GPU) fp32 벡터의 코사인 하한 */
	static final double MIN_COSINE = 0.999;

	private static final double NORM_TOLERANCE = 1e-3;

	private final LoadProperties props;

	private final StoreLoadService storeLoad;

	private final DataVersionRepository versions;

	private final EmbeddingClient embeddingClient;

	private final TransactionTemplate tx;

	private final JsonMapper json;

	private final Clock clock;

	public InitialLoadService(LoadProperties props, StoreLoadService storeLoad, DataVersionRepository versions,
			EmbeddingClient embeddingClient, TransactionTemplate tx, JsonMapper json, Clock clock) {
		this.props = props;
		this.storeLoad = storeLoad;
		this.versions = versions;
		this.embeddingClient = embeddingClient;
		this.tx = tx;
		this.json = json;
		this.clock = clock;
	}

	public Map<String, Object> prepare() {
		if (props.source() == null) {
			throw new IllegalArgumentException("--app.load.source=<원천 엑셀 경로>가 필요합니다");
		}
		if (storeLoad.countStores() > 0 || versions.count() > 0) {
			throw new IllegalStateException("첫 적재는 빈 DB에서만 합니다. 다시 하려면 infra에서 docker compose down -v 후 up -d");
		}
		Path dir = createDir();

		log.info("원천 읽기: {}", props.source());
		SourceFileReader.Result source = SourceFileReader.read(props.source());
		SourceMerger.Result merged = SourceMerger.merge(source.rows());
		log.info("원천 {}행(건너뜀 {}) → 가게 {}곳(여러 행을 합친 곳 {}곳, {}행)", source.rows().size(), source.problems().size(),
				merged.stores().size(), merged.mergedStores(), merged.mergedRows());

		LegacyStoreIds.Result legacy = null;
		if (props.legacySource() != null) {
			if (props.legacyRegistry() == null) {
				throw new IllegalArgumentException("--app.load.legacy-registry=<AI 레지스트리 CSV>가 필요합니다");
			}
			log.info("이전 id 읽기: {}", props.legacySource());
			legacy = LegacyStoreIds.build(props.legacySource(), props.legacyRegistry(), props.legacyQrels());
			log.info("이전 원천 {}행 중 AI id를 찾은 행 {}", legacy.sourceRows(), legacy.matchedRows());
		}
		Map<String, UUID> idByFingerprint = legacy == null ? Map.of() : legacy.idByFingerprint();

		record Prepared(DataVersionRepository.Version version, StoreLoadService.InsertResult inserted) {
		}
		Prepared prepared = tx.execute(status -> {
			DataVersionRepository.Version v = versions.createBuilding(DataVersionRepository.kstToday(clock), props.embeddingModel());
			return new Prepared(v, storeLoad.insertNew(v.id(), merged.stores(), idByFingerprint));
		});
		short versionId = prepared.version().id();
		StoreLoadService.InsertResult inserted = prepared.inserted();
		log.info("버전 {}(id {}) 만드는 중: 가게 {}곳, 이어받은 id {}, 새 id {}, 좌표 오류 {}", prepared.version().name(), versionId,
				inserted.total(), inserted.keptIds(), inserted.newIds(), inserted.coordErrors());

		List<SnapshotRow> rows = storeLoad.snapshotRows(versionId);
		Path input = dir.resolve(EmbedFiles.INPUT);
		EmbedFiles.writeInput(input, rows.stream().map(r -> new EmbedFiles.Entry(r.storeId(), r.docText())).toList());
		if (legacy != null) {
			writeAliases(dir.resolve("store_id_aliases.csv"), legacy.aliases());
		}

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("step", "prepare");
		report.put("versionId", versionId);
		report.put("versionName", prepared.version().name());
		report.put("embeddingModel", props.embeddingModel());
		report.put("sourceFile", props.source().getFileName().toString());
		report.put("sourceRows", source.rows().size());
		report.put("legacyColumns", source.legacyColumns());
		report.put("skippedRows", source.problems());
		report.put("stores", inserted.total());
		report.put("mergedStores", merged.mergedStores());
		report.put("mergedRows", merged.mergedRows());
		report.put("keptIds", inserted.keptIds());
		report.put("newIds", inserted.newIds());
		report.put("coordErrors", inserted.coordErrors());
		if (legacy != null) {
			report.put("legacySourceRows", legacy.sourceRows());
			report.put("legacyMatchedRows", legacy.matchedRows());
			report.put("legacyUnmatchedRowNumbers", legacy.unmatchedRows());
			report.put("aliases", legacy.aliases().size());
		}
		report.put("embedInput", input.getFileName().toString());
		report.put("embedInputSha256", EmbedFiles.sha256(input));
		writeJson(dir.resolve("prepare_report.json"), report);
		return report;
	}

	public Map<String, Object> importVectors() {
		DataVersionRepository.Version version = versions.findOnlyBuilding()
				.orElseThrow(() -> new IllegalStateException("만드는 중(building)인 버전이 없습니다. prepare를 먼저 하세요"));
		short versionId = version.id();
		Path dir = props.dir();

		EmbedFiles.Manifest manifest = json.readValue(dir.resolve(EmbedFiles.MANIFEST).toFile(), EmbedFiles.Manifest.class);
		Path inputPath = dir.resolve(EmbedFiles.INPUT);
		String inputSha = EmbedFiles.sha256(inputPath);
		List<EmbedFiles.Entry> inputs = EmbedFiles.readInput(inputPath);
		NpyFile npy = NpyFile.open(dir.resolve(EmbedFiles.VECTORS));
		check(inputSha.equals(manifest.inputSha256()), "Colab이 읽은 입력 파일이 지금 파일과 다릅니다(sha256)");
		check(version.embeddingModel().equals(manifest.model()),
				"모델이 다릅니다: 버전 " + version.embeddingModel() + ", Colab " + manifest.model());
		check(manifest.dimension() == EmbeddingClient.DIMENSION && npy.dimension() == EmbeddingClient.DIMENSION, "벡터 차원이 1024가 아닙니다");
		check(manifest.count() == inputs.size() && npy.rows() == inputs.size(),
				"건수가 다릅니다: 입력 " + inputs.size() + ", 설명 " + manifest.count() + ", 벡터 " + npy.rows());

		List<SnapshotRow> rows = storeLoad.snapshotRows(versionId);
		Map<UUID, SnapshotRow> byId = new HashMap<>(rows.size() * 2);
		rows.forEach(r -> byId.put(r.storeId(), r));
		check(byId.size() == inputs.size(), "DB 가게 수(" + byId.size() + ")와 입력 파일 행 수(" + inputs.size() + ")가 다릅니다");
		for (EmbedFiles.Entry e : inputs) {
			SnapshotRow r = byId.get(e.storeId());
			check(r != null, "DB에 없는 가게: " + e.storeId());
			check(r.docText().equals(e.docText()), "임베딩 원문이 DB 값과 다릅니다: " + e.storeId());
		}

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("step", "import");
		report.put("versionId", versionId);
		report.put("versionName", version.name());
		if (props.verifyWithServer()) {
			report.put("serverCheckMinCosine", compareWithServer(inputs, npy));
		}

		long started = System.nanoTime();
		long copied;
		try (NpyFile.Sequential vectors = npy.sequential()) {
			Iterator<EmbedFiles.Entry> it = inputs.iterator();
			int[] index = {0};
			Iterator<SnapshotPartitionRepository.Entry> entries = new Iterator<>() {
				@Override
				public boolean hasNext() {
					return it.hasNext();
				}

				@Override
				public SnapshotPartitionRepository.Entry next() {
					EmbedFiles.Entry e = it.next();
					float[] v = vectors.next();
					double norm = 0;
					for (float x : v) {
						norm += x * x;
					}
					if (Math.abs(Math.sqrt(norm) - 1) > NORM_TOLERANCE) {
						throw new IllegalStateException(index[0] + "번째 벡터가 정규화되어 있지 않습니다(길이 " + Math.sqrt(norm) + ")");
					}
					index[0]++;
					return new SnapshotPartitionRepository.Entry(byId.get(e.storeId()), v);
				}
			};
			copied = storeLoad.writeSnapshot(versionId, entries);
		}
		log.info("스냅샷 {}행 채움·인덱스 {}초", copied, (System.nanoTime() - started) / 1_000_000_000);

		Map<String, Long> checks = storeLoad.verifySnapshot(versionId);
		check(checks.values().stream().allMatch(n -> n == 0), "전환 전 검증 실패: " + checks);

		int notSearchable = (int) rows.stream().filter(r -> !r.searchable()).count();
		DataVersionRepository.Counts counts = new DataVersionRepository.Counts(rows.size(), rows.size(), 0, 0, 0, notSearchable);
		tx.executeWithoutResult(status -> {
			storeLoad.attachSnapshot(versionId);
			versions.activate(versionId, counts);
		});
		log.info("버전 {} 활성", version.name());

		report.put("rows", copied);
		report.put("searchable", rows.size() - notSearchable);
		report.put("coordErrors", notSearchable);
		report.put("checks", checks);
		report.put("seconds", (System.nanoTime() - started) / 1_000_000_000);
		writeJson(dir.resolve("import_report.json"), report);
		return report;
	}

	/** 표본을 로컬 임베딩 서버로 다시 계산해 Colab 벡터와 비교한다. 가장 낮은 코사인을 돌려준다. */
	private double compareWithServer(List<EmbedFiles.Entry> inputs, NpyFile npy) {
		int sample = Math.max(1, Math.min(Math.min(props.verifySample(), 64), inputs.size()));
		List<Integer> picks = new ArrayList<>(sample);
		for (int i = 0; i < sample; i++) {
			picks.add((int) ((long) i * inputs.size() / sample));
		}
		List<float[]> local;
		try {
			local = embeddingClient.embedBatch(picks.stream().map(i -> inputs.get(i).docText()).toList()).vectors();
		}
		catch (RuntimeException e) {
			throw new IllegalStateException("표본 확인에 로컬 임베딩 서버가 필요합니다(docker compose up -d). "
					+ "확인을 건너뛰려면 --app.load.verify-with-server=false", e);
		}
		double min = 1;
		for (int k = 0; k < picks.size(); k++) {
			float[] a = npy.row(picks.get(k));
			float[] b = local.get(k);
			double dot = 0;
			for (int j = 0; j < a.length; j++) {
				dot += a[j] * b[j];
			}
			min = Math.min(min, dot);
		}
		log.info("표본 {}개 로컬 서버 대비 최소 코사인 {}", sample, min);
		check(min >= MIN_COSINE, "Colab 벡터가 로컬 서버 결과와 다릅니다(최소 코사인 " + min + "). 모델·설정을 확인하세요");
		return min;
	}

	private Path createDir() {
		try {
			return Files.createDirectories(props.dir());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void writeJson(Path file, Map<String, Object> report) {
		json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
	}

	private static void writeAliases(Path file, List<LegacyStoreIds.Alias> aliases) {
		try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			w.write(Csv.line("old_store_id", "store_id"));
			for (LegacyStoreIds.Alias a : aliases) {
				w.write(Csv.line(a.oldId().toString(), a.keptId().toString()));
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

}

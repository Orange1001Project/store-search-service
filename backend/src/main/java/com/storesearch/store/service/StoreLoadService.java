package com.storesearch.store.service;

import com.storesearch.store.repository.SnapshotPartitionRepository;
import com.storesearch.store.repository.StoreBulkRepository;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가게 원천 값 적재와 검색 스냅샷 파티션 만들기(store 도메인 쪽). 순서·전환은 refresh 도메인이 정한다.
 * 스냅샷 값은 {@link SnapshotRowFactory}로만 만든다.
 */
@Service
public class StoreLoadService {

	public record InsertResult(int total, int keptIds, int newIds, int coordErrors) {
	}

	private final StoreBulkRepository stores;

	private final SnapshotPartitionRepository partitions;

	private final SnapshotRowFactory snapshotRows;

	public StoreLoadService(StoreBulkRepository stores, SnapshotPartitionRepository partitions, SnapshotRowFactory snapshotRows) {
		this.stores = stores;
		this.partitions = partitions;
		this.snapshotRows = snapshotRows;
	}

	public long countStores() {
		return stores.count();
	}

	/** 지문으로 이어받을 id가 있으면 그 id, 없으면 새 UUID. 호출하는 쪽 트랜잭션 안에서. */
	public InsertResult insertNew(short versionId, List<StoreSource> sources, Map<String, UUID> idByFingerprint) {
		List<StoreBulkRepository.NewStore> rows = new ArrayList<>(sources.size());
		int kept = 0;
		int coordErrors = 0;
		for (StoreSource s : sources) {
			UUID id = idByFingerprint.get(s.fingerprint());
			if (id == null) {
				id = UUID.randomUUID();
			}
			else {
				kept++;
			}
			if (s.coordError()) {
				coordErrors++;
			}
			rows.add(new StoreBulkRepository.NewStore(id, s));
		}
		stores.insertAll(versionId, rows);
		return new InsertResult(rows.size(), kept, rows.size() - kept, coordErrors);
	}

	/** 그 버전에서 원천에 있던 가게의 스냅샷 행(store_id 오름차순) */
	@Transactional(readOnly = true)
	public List<SnapshotRow> snapshotRows(short versionId) {
		List<SnapshotRow> rows = new ArrayList<>();
		stores.forEachSeenIn(versionId, s -> rows.add(snapshotRows.build(s.storeId(), s.source(), s.lifecycleStatus(), s.hidden())));
		return rows;
	}

	/** 새 파티션 표를 만들어 채우고 인덱스까지. 아직 붙이지 않는다. 넣은 행 수. */
	public long writeSnapshot(short versionId, Iterator<SnapshotPartitionRepository.Entry> entries) {
		partitions.createEmpty(versionId);
		long n = partitions.copy(versionId, entries);
		partitions.finish(versionId);
		return n;
	}

	public Map<String, Long> verifySnapshot(short versionId) {
		return partitions.verify(versionId);
	}

	/** 전환 트랜잭션 안에서 부른다 */
	public void attachSnapshot(short versionId) {
		partitions.attach(versionId);
	}

}

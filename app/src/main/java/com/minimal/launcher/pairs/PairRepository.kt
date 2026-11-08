package com.minimal.launcher.pairs

import com.minimal.launcher.data.db.AppPairEntity
import com.minimal.launcher.data.db.PairDao
import kotlinx.coroutines.flow.Flow

class PairRepository(private val dao: PairDao) {
    val pairs: Flow<List<AppPairEntity>> = dao.observeAll()

    /** 2 or 3 distinct apps; returns null when the selection is not valid. */
    suspend fun create(appKeys: List<String>): Long? {
        val keys = appKeys.distinct()
        if (keys.size !in 2..3) return null
        val encoded = AppPairEntity.encode(keys)
        dao.getAll().firstOrNull { it.apps == encoded }?.let { return it.id }
        return dao.insert(AppPairEntity(apps = encoded, position = dao.nextPosition()))
    }

    /** null = automatic (category of the first app). */
    suspend fun setCategory(id: Long, categoryId: Long?) {
        val p = dao.get(id) ?: return
        dao.update(p.copy(categoryId = categoryId))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun get(id: Long) = dao.get(id)
}

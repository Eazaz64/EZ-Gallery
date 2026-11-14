package com.eazaz.fastgallery.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders", indices = [Index("root")])
data class FolderEntity(
    @PrimaryKey val path: String,
    val name: String,
    val root: String,
    val imageCount: Int,
    val totalSize: Long,
    val coverPath: String?,
    val coverMtime: Long,
    val dirMtime: Long,
    val newestTaken: Long,
    val newestModified: Long,
    val pinned: Boolean = false,
    val pinOrder: Long = 0,
    val sortKey: String? = null,
    val sortAsc: Boolean? = null,
)

@Entity(tableName = "images", indices = [Index("folder")])
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folder: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val taken: Long,
)

val ImageEntity.path: String get() = "$folder/$name"
val ImageEntity.ext: String get() = name.substringAfterLast('.', "").lowercase()

data class FolderStats(val c: Int, val s: Long, val t: Long?, val m: Long?)

@Dao
abstract class GalleryDao {
    @Query("SELECT * FROM folders")
    abstract fun foldersFlow(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    abstract suspend fun allFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE path = :path")
    abstract fun folderFlow(path: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE path = :path")
    abstract suspend fun folder(path: String): FolderEntity?

    @Query("SELECT * FROM images WHERE folder = :folder")
    abstract fun imagesFlow(folder: String): Flow<List<ImageEntity>>

    @Query("SELECT name FROM images WHERE folder = :folder")
    abstract suspend fun imageNames(folder: String): List<String>

    @Query("SELECT * FROM images WHERE folder = :folder ORDER BY taken DESC LIMIT 1")
    abstract suspend fun newestImage(folder: String): ImageEntity?

    @Query("SELECT COUNT(*) AS c, COALESCE(SUM(size), 0) AS s, MAX(taken) AS t, MAX(modified) AS m FROM images WHERE folder = :folder")
    abstract suspend fun stats(folder: String): FolderStats

    @Upsert
    abstract suspend fun upsertFolder(f: FolderEntity)

    @Insert
    abstract suspend fun insertImages(list: List<ImageEntity>)

    @Query("DELETE FROM images WHERE folder = :folder")
    abstract suspend fun deleteImagesOf(folder: String)

    @Query("DELETE FROM folders WHERE path = :path")
    abstract suspend fun deleteFolderRow(path: String)

    @Query("DELETE FROM images WHERE id IN (:ids)")
    abstract suspend fun deleteImageIds(ids: List<Long>)

    @Query("UPDATE images SET folder = :folder, name = :name WHERE id = :id")
    abstract suspend fun relocateImage(id: Long, folder: String, name: String)

    @Query("UPDATE folders SET pinned = :pinned, pinOrder = :order WHERE path = :path")
    abstract suspend fun setPinned(path: String, pinned: Boolean, order: Long)

    @Query("UPDATE folders SET sortKey = :key, sortAsc = :asc WHERE path = :path")
    abstract suspend fun setFolderSort(path: String, key: String?, asc: Boolean?)

    @Query("UPDATE folders SET sortKey = NULL, sortAsc = NULL")
    abstract suspend fun clearFolderSorts()

    @Query("UPDATE folders SET name = :name WHERE path = :path")
    abstract suspend fun setFolderName(path: String, name: String)

    @Query(
        "UPDATE images SET folder = :newP || substr(folder, length(:oldP) + 1) " +
            "WHERE folder = :oldP OR substr(folder, 1, length(:oldP) + 1) = :oldP || '/'"
    )
    abstract suspend fun renameImagesPrefix(oldP: String, newP: String)

    @Query(
        "UPDATE folders SET path = :newP || substr(path, length(:oldP) + 1), " +
            "coverPath = CASE WHEN coverPath IS NULL THEN NULL ELSE :newP || substr(coverPath, length(:oldP) + 1) END " +
            "WHERE path = :oldP OR substr(path, 1, length(:oldP) + 1) = :oldP || '/'"
    )
    abstract suspend fun renameFoldersPrefix(oldP: String, newP: String)

    @Transaction
    open suspend fun replaceFolder(f: FolderEntity, imgs: List<ImageEntity>) {
        deleteImagesOf(f.path)
        imgs.chunked(2000).forEach { insertImages(it) }
        upsertFolder(f)
    }

    @Transaction
    open suspend fun removeFolder(path: String) {
        deleteImagesOf(path)
        deleteFolderRow(path)
    }

    @Transaction
    open suspend fun renameFolderTree(oldP: String, newP: String, newName: String) {
        renameImagesPrefix(oldP, newP)
        renameFoldersPrefix(oldP, newP)
        setFolderName(newP, newName)
    }
}

@Database(entities = [FolderEntity::class, ImageEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): GalleryDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "gallery.db")
                .fallbackToDestructiveMigration(true)
                .build().also { inst = it }
        }
    }
}

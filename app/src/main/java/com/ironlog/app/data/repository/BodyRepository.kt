package com.ironlog.app.data.repository

import com.ironlog.app.core.media.PhotoStore
import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.entity.BodyMeasurementEntity
import com.ironlog.app.data.entity.PhotoPose
import com.ironlog.app.data.entity.ProgressPhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Measurement input, already converted to kilograms and centimetres by the caller. */
data class MeasurementInput(
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val leftArmCm: Double? = null,
    val rightArmCm: Double? = null,
    val thighCm: Double? = null,
    val calfCm: Double? = null,
    val neckCm: Double? = null,
    val note: String = ""
) {
    val isEmpty: Boolean
        get() = listOf(weightKg, bodyFatPercent, chestCm, waistCm, hipsCm, leftArmCm, rightArmCm, thighCm, calfCm, neckCm)
            .all { it == null }
}

interface BodyRepository {
    fun observeMeasurements(): Flow<List<BodyMeasurementEntity>>
    fun observeLatestMeasurement(): Flow<BodyMeasurementEntity?>
    fun observePhotos(): Flow<List<ProgressPhotoEntity>>
    suspend fun addMeasurement(input: MeasurementInput): Result<Unit>
    suspend fun deleteMeasurement(item: BodyMeasurementEntity): Result<Unit>

    /** Takes ownership of a file already written to private storage by [PhotoStore]. */
    suspend fun addPhoto(filePath: String, pose: PhotoPose, note: String): Result<Unit>
    suspend fun deletePhoto(id: String): Result<Unit>
}

@Singleton
class BodyRepositoryImpl @Inject constructor(
    private val bodyDao: BodyDao,
    private val userDao: UserDao,
    private val photoStore: PhotoStore,
    private val currentUser: CurrentUser
) : BodyRepository {

    override fun observeMeasurements(): Flow<List<BodyMeasurementEntity>> =
        currentUser.idOrNull()?.let { bodyDao.observeMeasurements(it) } ?: flowOf(emptyList())

    override fun observeLatestMeasurement(): Flow<BodyMeasurementEntity?> =
        currentUser.idOrNull()?.let { bodyDao.observeLatestMeasurement(it) } ?: flowOf(null)

    override fun observePhotos(): Flow<List<ProgressPhotoEntity>> =
        currentUser.idOrNull()?.let { bodyDao.observePhotos(it) } ?: flowOf(emptyList())

    override suspend fun addMeasurement(input: MeasurementInput): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            if (input.isEmpty) throw UserFacingException("Enter at least one measurement")
            input.weightKg?.let { if (it !in 20.0..400.0) throw UserFacingException("Check the bodyweight") }
            input.bodyFatPercent?.let { if (it !in 2.0..70.0) throw UserFacingException("Body fat must be 2-70%") }

            bodyDao.upsertMeasurement(
                BodyMeasurementEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    takenAt = System.currentTimeMillis(),
                    weightKg = input.weightKg,
                    bodyFatPercent = input.bodyFatPercent,
                    chestCm = input.chestCm,
                    waistCm = input.waistCm,
                    hipsCm = input.hipsCm,
                    leftArmCm = input.leftArmCm,
                    rightArmCm = input.rightArmCm,
                    thighCm = input.thighCm,
                    calfCm = input.calfCm,
                    neckCm = input.neckCm,
                    note = Validators.sanitiseText(input.note, 300)
                )
            )
            // The latest weigh-in becomes the profile bodyweight, which feeds the
            // cardio calorie estimate and bodyweight-exercise volume.
            input.weightKg?.let { weight ->
                userDao.findById(userId)?.let { userDao.update(it.copy(bodyweightKg = weight)) }
            }
            Unit
        }
    }

    override suspend fun deleteMeasurement(item: BodyMeasurementEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (item.userId != currentUser.require()) throw UserFacingException("Measurement not found")
            bodyDao.deleteMeasurement(item)
        }
    }

    override suspend fun addPhoto(filePath: String, pose: PhotoPose, note: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = currentUser.require()
                if (!photoStore.exists(filePath)) throw UserFacingException("The photo could not be saved")
                val bodyweight = userDao.findById(userId)?.bodyweightKg
                bodyDao.upsertPhoto(
                    ProgressPhotoEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        filePath = filePath,
                        takenAt = System.currentTimeMillis(),
                        pose = pose,
                        bodyweightKg = bodyweight,
                        note = Validators.sanitiseText(note, 300)
                    )
                )
            }
        }

    /** Removes the database row and the image file together, so no orphan remains on disk. */
    override suspend fun deletePhoto(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val photo = bodyDao.findPhoto(id) ?: throw UserFacingException("Photo not found")
            if (photo.userId != currentUser.require()) throw UserFacingException("Photo not found")
            bodyDao.deletePhoto(photo)
            photoStore.delete(photo.filePath)
            Unit
        }
    }
}

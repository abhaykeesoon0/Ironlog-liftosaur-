package com.ironlog.app.data.repository

import androidx.room.withTransaction
import com.ironlog.app.core.database.AppDatabase
import com.ironlog.app.data.dao.RewardsDao
import com.ironlog.app.data.entity.QuestClaimEntity
import com.ironlog.app.data.entity.StorePurchaseEntity
import com.ironlog.app.domain.usecase.Quests
import com.ironlog.app.domain.usecase.StoreCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface RewardsRepository {
    /** Coins earned less coins spent. */
    fun observeBalance(): Flow<Int>

    /** Claims inside the periods currently open, which is what "claimed" means today. */
    fun observeClaims(periodKeys: List<String>): Flow<List<QuestClaimEntity>>

    fun observePurchases(): Flow<List<StorePurchaseEntity>>

    suspend fun claim(questId: String, periodKey: String): Result<Int>
    suspend fun buy(itemId: String): Result<Unit>
    suspend fun equip(itemId: String): Result<Unit>
}

@Singleton
class RewardsRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val rewardsDao: RewardsDao,
    private val currentUser: CurrentUser
) : RewardsRepository {

    override fun observeBalance(): Flow<Int> =
        currentUser.idOrNull()?.let { rewardsDao.observeBalance(it) } ?: flowOf(0)

    override fun observeClaims(periodKeys: List<String>): Flow<List<QuestClaimEntity>> =
        currentUser.idOrNull()?.let { rewardsDao.observeClaims(it, periodKeys) } ?: flowOf(emptyList())

    override fun observePurchases(): Flow<List<StorePurchaseEntity>> =
        currentUser.idOrNull()?.let { rewardsDao.observePurchases(it) } ?: flowOf(emptyList())

    /**
     * Pays out a quest, once per period.
     *
     * The payout is read from the catalogue rather than taken from the caller,
     * so a screen cannot ask for an amount of its own choosing, and the whole
     * thing runs in a transaction: two taps arriving together must not both see
     * an unclaimed quest and both pay.
     */
    override suspend fun claim(questId: String, periodKey: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = currentUser.require()
                val quest = Quests.byId(questId) ?: throw UserFacingException("That quest no longer exists")

                database.withTransaction {
                    if (rewardsDao.claimCount(userId, questId, periodKey) > 0) {
                        throw UserFacingException("You have already claimed that one")
                    }
                    rewardsDao.upsertClaim(
                        QuestClaimEntity(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            questId = questId,
                            periodKey = periodKey,
                            coins = quest.coins,
                            claimedAt = System.currentTimeMillis()
                        )
                    )
                }
                quest.coins
            }
        }

    /**
     * Buys an item, if it is affordable and not already owned.
     *
     * The balance check and the purchase share a transaction for the same
     * reason as a claim: without it, two quick taps could each read the old
     * balance and spend the same coins twice.
     */
    override suspend fun buy(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val item = StoreCatalogue.byId(itemId) ?: throw UserFacingException("That item is no longer stocked")

            database.withTransaction {
                if (rewardsDao.findPurchase(userId, itemId) != null) {
                    throw UserFacingException("You already own that")
                }
                val balance = rewardsDao.balance(userId)
                if (balance < item.price) {
                    val short = item.price - balance
                    throw UserFacingException("$short more ${if (short == 1) "coin" else "coins"} needed")
                }
                rewardsDao.upsertPurchase(
                    StorePurchaseEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        itemId = itemId,
                        coinsSpent = item.price,
                        purchasedAt = System.currentTimeMillis(),
                        isEquipped = false
                    )
                )
            }
        }
    }

    /**
     * Equips an owned item, unequipping whatever else held that slot.
     *
     * Backgrounds and badges are one-at-a-time; widgets are not, since having
     * two cards on Home is the point of buying two. A free item -- the default
     * background -- is recorded as a zero-cost purchase the first time it is
     * chosen, so "owned" stays a single question with a single answer.
     */
    override suspend fun equip(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val item = StoreCatalogue.byId(itemId) ?: throw UserFacingException("That item is no longer stocked")

            database.withTransaction {
                val owned = rewardsDao.findPurchase(userId, itemId)
                    ?: if (item.price == 0) {
                        StorePurchaseEntity(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            itemId = itemId,
                            coinsSpent = 0,
                            purchasedAt = System.currentTimeMillis()
                        ).also { rewardsDao.upsertPurchase(it) }
                    } else {
                        throw UserFacingException("Buy it first")
                    }

                if (item.category != StoreCatalogue.Category.WIDGET) {
                    val siblings = StoreCatalogue.inCategory(item.category).map { it.id }
                    rewardsDao.purchasesIn(userId, siblings)
                        .filter { it.isEquipped && it.itemId != itemId }
                        .forEach { rewardsDao.upsertPurchase(it.copy(isEquipped = false)) }
                }

                // A widget toggles: tapping the one already on Home takes it off
                // again. A background or badge is a slot, so equipping simply
                // fills it -- tapping the current one is not a request to have
                // no background at all.
                val nextEquipped =
                    if (item.category == StoreCatalogue.Category.WIDGET) !owned.isEquipped else true
                rewardsDao.upsertPurchase(owned.copy(isEquipped = nextEquipped))
            }
        }
    }
}

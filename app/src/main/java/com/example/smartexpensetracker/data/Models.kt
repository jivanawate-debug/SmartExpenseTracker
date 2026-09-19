package com.example.smartexpensetracker.data

import androidx.room.*

@Entity(tableName="expenses")
data class Expense(@PrimaryKey(autoGenerate=true) val id:Long=0,val amount:Double,val category:String,val merchant:String,val note:String="",val dateMillis:Long,val source:String="Manual",val fingerprint:String="",val needsReview:Boolean=false,val transactionType:String="Debit",val status:String="Completed")
@Entity(tableName="budgets")
data class Budget(@PrimaryKey val category:String,val monthlyLimit:Double)
@Entity(tableName="merchant_rules")
data class MerchantRule(@PrimaryKey val merchant:String,val category:String)
@Entity(tableName="recurring")
data class Recurring(@PrimaryKey(autoGenerate=true) val id:Long=0,val merchant:String,val amount:Double,val category:String,val nextDueMillis:Long,val frequency:String="Monthly",val active:Boolean=true)
@Dao interface ExpenseDao { @Query("SELECT * FROM expenses ORDER BY dateMillis DESC") fun all():kotlinx.coroutines.flow.Flow<List<Expense>>; @Insert fun insert(e:Expense):Long; @Update fun update(e:Expense); @Delete fun delete(e:Expense); @Query("SELECT * FROM expenses WHERE id=:id LIMIT 1") fun byId(id:Long):Expense?; @Query("SELECT * FROM expenses WHERE dateMillis BETWEEN :a AND :b ORDER BY dateMillis DESC") fun range(a:Long,b:Long):List<Expense>; @Query("SELECT * FROM expenses WHERE fingerprint=:fp LIMIT 1") fun fingerprint(fp:String):Expense? }
@Dao interface BudgetDao { @Query("SELECT * FROM budgets") fun all():kotlinx.coroutines.flow.Flow<List<Budget>>; @Insert(onConflict=OnConflictStrategy.REPLACE) fun save(b:Budget); @Delete fun delete(b:Budget) }
@Dao interface RuleDao { @Query("SELECT * FROM merchant_rules") fun all():kotlinx.coroutines.flow.Flow<List<MerchantRule>>; @Insert(onConflict=OnConflictStrategy.REPLACE) fun save(r:MerchantRule) }
@Dao interface RecurringDao { @Query("SELECT * FROM recurring WHERE active=1 ORDER BY nextDueMillis") fun all():kotlinx.coroutines.flow.Flow<List<Recurring>>; @Insert(onConflict=OnConflictStrategy.REPLACE) fun save(r:Recurring); @Delete fun delete(r:Recurring) }
@Database(entities=[Expense::class,Budget::class,MerchantRule::class,Recurring::class],version=1,exportSchema=false)
abstract class AppDb:RoomDatabase(){abstract fun expenses():ExpenseDao;abstract fun budgets():BudgetDao;abstract fun rules():RuleDao;abstract fun recurring():RecurringDao}

package com.example.smartexpensetracker.automation
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.room.Room
import com.example.smartexpensetracker.data.AppDb
import com.example.smartexpensetracker.data.Expense
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
class TransactionNotificationListener:NotificationListenerService(){ private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO); private lateinit var db:AppDb
 override fun onCreate(){super.onCreate();db=Room.databaseBuilder(applicationContext,AppDb::class.java,"expenses.db").build()}
 override fun onNotificationPosted(s:StatusBarNotification){ val extras=s.notification.extras; val text=listOf(extras.getCharSequence("android.title"),extras.getCharSequence("android.text"),extras.getCharSequence("android.bigText")).filterNotNull().joinToString(" "); scope.launch{val p=Parser.parse(text,db.rules().all().first()); if(p!=null&&p.type=="Debit"&&db.expenses().fingerprint(p.fingerprint)==null) db.expenses().insert(Expense(amount=p.amount,category=p.category,merchant=p.merchant,dateMillis=System.currentTimeMillis(),source="Notification",fingerprint=p.fingerprint,needsReview=p.confidence<85))}}
 override fun onDestroy(){scope.cancel();super.onDestroy()}}

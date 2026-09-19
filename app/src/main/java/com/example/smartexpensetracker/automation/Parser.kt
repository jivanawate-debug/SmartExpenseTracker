package com.example.smartexpensetracker.automation
import com.example.smartexpensetracker.data.*

data class Parsed(val amount:Double,val merchant:String,val category:String,val type:String,val confidence:Int,val fingerprint:String)
object Parser {
 fun parse(text:String,rules:List<MerchantRule>):Parsed? { val t=text.lowercase(); if(listOf("otp","verification","declined","failed").any{t.contains(it)}) return null
  val m=Regex("(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",RegexOption.IGNORE_CASE).find(text) ?: Regex("(?:amount|spent|debited|paid)\\D{0,12}([0-9,]+(?:\\.[0-9]{1,2})?)",RegexOption.IGNORE_CASE).find(text) ?: return null
  val amount=m.groupValues[1].replace(",","").toDoubleOrNull() ?: return null
  val merchant=(Regex("(?:at|to|merchant)\\s+([A-Za-z0-9&._-]{2,30})",RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "Unknown Merchant").trim()
  val rule=rules.firstOrNull{t.contains(it.merchant.lowercase())}; val category=rule?.category ?: when{listOf("swiggy","zomato","restaurant","food","domino","pizza").any{t.contains(it)}->"Food";listOf("uber","ola","rapido","metro","fuel","petrol").any{t.contains(it)}->"Travel";listOf("amazon","flipkart","myntra","shopping").any{t.contains(it)}->"Shopping";listOf("electricity","water bill","mobile bill","broadband","recharge").any{t.contains(it)}->"Bills";listOf("hospital","pharmacy","medical","health").any{t.contains(it)}->"Health";else->"Other"}
  val type=when{listOf("credited","received","refund").any{t.contains(it)}->"Credit";listOf("debited","spent","paid","purchase","sent").any{t.contains(it)}->"Debit";else->"Unknown"}; val c=55+(if(merchant!="Unknown Merchant")20 else 0)+(if(category!="Other")15 else 0)+(if(type!="Unknown")10 else 0); val fp="%.2f|${merchant.lowercase()}|$category".format(amount); return Parsed(amount,merchant,category,type,c,fp)
 }
}

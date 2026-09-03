package com.example

import com.example.sms.SmsParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testBkashParser() {
    val sender = "bKash"
    val body = "You have received Tk 50.00 from 01887353914. Ref . Fee Tk 0.00. Balance Tk 100.00. TrxID 75XCY7X1 at 03/09/2026 12:30"
    val result = SmsParser.parseMessage(sender, body)
    assertNotNull(result)
    assertEquals("bKash", result?.mfsName)
    assertEquals("50.00", result?.amount)
    assertEquals("75XCY7X1", result?.trxId)
    assertEquals("01887353914", result?.senderNumber)
    assertEquals("100.00", result?.currentBalance)
  }

  @Test
  fun testNagadParser() {
    val sender = "NAGAD"
    val body = "Deposit of Tk 50.00 from 01887353914. Fee: Tk 0.00. Balance: Tk 50.00. TrxID: 75XCY7X1"
    val result = SmsParser.parseMessage(sender, body)
    assertNotNull(result)
    assertEquals("Nagad", result?.mfsName)
    assertEquals("50.00", result?.amount)
    assertEquals("75XCY7X1", result?.trxId)
    assertEquals("01887353914", result?.senderNumber)
  }

  @Test
  fun testRocket16216Parser() {
    val sender = "16216"
    val body = "You have received Tk 500.00 from A/C: 017123456789. TxnId: 240902123456. Balance: Tk 1,500.00"
    val result = SmsParser.parseMessage(sender, body)
    assertNotNull(result)
    assertEquals("Rocket", result?.mfsName)
    assertEquals("500.00", result?.amount)
    assertEquals("240902123456", result?.trxId)
    assertEquals("017123456789", result?.senderNumber)
  }
}

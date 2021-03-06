package com.coinffeine.client.handshake

import scala.collection.JavaConversions._
import scala.util.Try

import com.google.bitcoin.core.{Transaction, Wallet}
import com.google.bitcoin.core.Transaction.SigHash
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType
import com.google.bitcoin.crypto.TransactionSignature
import com.google.bitcoin.script.ScriptBuilder

import com.coinffeine.client.ExchangeInfo
import com.coinffeine.common.currency.BtcAmount
import com.coinffeine.common.currency.Implicits._

abstract class DefaultHandshake(
    val exchangeInfo: ExchangeInfo,
    amountToCommit: BtcAmount,
    userWallet: Wallet) extends Handshake {
  require(userWallet.hasKey(exchangeInfo.userKey),
    "User wallet does not contain the user's private key")
  require(amountToCommit > (0 BTC), "Amount to commit must be greater than zero")

  private val inputFunds = {
    val inputFundCandidates = userWallet.calculateAllSpendCandidates(true)
    val necessaryInputCount = inputFundCandidates.view
      .scanLeft(0 BTC)((accum, output) => accum + BtcAmount(output.getValue))
      .takeWhile(_ < amountToCommit)
      .length
    inputFundCandidates.take(necessaryInputCount)
  }
  private val totalInputFunds = inputFunds.map(funds => BtcAmount(funds.getValue)).sum
  require(totalInputFunds >= amountToCommit,
    "Input funds must cover the amount of funds to commit")

  override val commitmentTransaction: Transaction = {
    val tx = new Transaction(exchangeInfo.network)
    inputFunds.foreach(tx.addInput)
    val changeAmount = totalInputFunds - amountToCommit
    require(changeAmount >= (0 BTC))
    tx.addOutput(
      amountToCommit.asSatoshi,
      ScriptBuilder.createMultiSigOutputScript(
        2, List(exchangeInfo.counterpartKey, exchangeInfo.userKey)))
    if (changeAmount > (0 BTC)) {
      tx.addOutput(
        (totalInputFunds - amountToCommit).asSatoshi,
        userWallet.getChangeAddress)
    }
    tx.signInputs(SigHash.ALL, userWallet)
    tx
  }
  private val committedFunds = commitmentTransaction.getOutput(0)
  override val refundTransaction: Transaction = {
    val tx = new Transaction(exchangeInfo.network)
    tx.setLockTime(exchangeInfo.lockTime)
    tx.addInput(committedFunds).setSequenceNumber(0)
    tx.addOutput(committedFunds.getValue, exchangeInfo.userKey)
    ensureValidRefundTransaction(tx)
    tx
  }

  override def signCounterpartRefundTransaction(
      counterpartRefundTx: Transaction): Try[TransactionSignature] = Try {
    ensureValidRefundTransaction(counterpartRefundTx)
    val connectedPubKeyScript = ScriptBuilder.createMultiSigOutputScript(
      2, List(exchangeInfo.userKey, exchangeInfo.counterpartKey))
    counterpartRefundTx.calculateSignature(
      0, exchangeInfo.userKey, connectedPubKeyScript, SigHash.ALL, false)
  }

  private def ensureValidRefundTransaction(refundTx: Transaction) = {
    // TODO: Is this enough to ensure we can sign?
    require(refundTx.isTimeLocked)
    require(refundTx.getLockTime == exchangeInfo.lockTime)
    require(refundTx.getInputs.size == 1)
    require(refundTx.getConfidence.getConfidenceType == ConfidenceType.UNKNOWN)
  }

  override def validateRefundSignature(signature: TransactionSignature): Try[Unit] = Try {
    val input = refundTransaction.getInput(0)
    require(exchangeInfo.counterpartKey.verify(
      refundTransaction.hashForSignature(
        0,
        input.getConnectedOutput.getScriptPubKey,
        SigHash.ALL,
        false),
      signature))
  }
}

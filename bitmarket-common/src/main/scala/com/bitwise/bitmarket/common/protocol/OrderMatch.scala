package com.bitwise.bitmarket.common.protocol

import com.bitwise.bitmarket.common.PeerConnection
import com.bitwise.bitmarket.common.currency.{FiatAmount, BtcAmount}

/** Represents a coincidence of desires of both a buyer and a seller */
case class OrderMatch(
    orderMatchId: String,
    amount: BtcAmount,
    price: FiatAmount,
    buyer: PeerConnection,
    seller: PeerConnection
)

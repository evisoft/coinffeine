package com.coinffeine.client.peer

import akka.actor._

import com.coinffeine.client.peer.QuoteRequestActor.StartRequest
import com.coinffeine.common.{AkkaSpec, PeerConnection}
import com.coinffeine.common.currency.CurrencyCode._
import com.coinffeine.common.protocol.gateway.GatewayProbe
import com.coinffeine.common.protocol.messages.brokerage.{Quote, QuoteRequest}

class QuoteRequestActorTest extends AkkaSpec(ActorSystem("RequestActorTest")) {

  val broker = PeerConnection("broker")
  val gateway = new GatewayProbe()
  val actor = system.actorOf(Props(new QuoteRequestActor()), "ask-for-quotes")

  "A quote request actor" should "subscribe to messages" in {
    gateway.expectNoMsg()
    actor ! StartRequest(EUR.currency, gateway.ref, broker)
    gateway.expectSubscription()
  }

  it should "forward the request to the broker" in {
    gateway.expectForwarding(QuoteRequest(EUR.currency), broker)
  }

  it should "forward the reply to the requester" in {
    gateway.relayMessage(Quote.empty(EUR.currency), broker)
    expectMsg(Quote.empty(EUR.currency))
  }
}

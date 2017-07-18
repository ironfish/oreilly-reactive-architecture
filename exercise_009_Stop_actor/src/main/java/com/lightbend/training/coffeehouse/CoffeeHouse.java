/**
 * Copyright © 2014, 2015 Typesafe, Inc. All rights reserved. [http://www.typesafe.com]
 */

package com.lightbend.training.coffeehouse;

import akka.actor.AbstractActor;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;


public class CoffeeHouse extends AbstractLoggingActor {


    private final FiniteDuration baristaPrepareCoffeeDuration =
            Duration.create(
                    context().system().settings().config().getDuration(
                            "coffee-house.barista.prepare-coffee-duration", MILLISECONDS), MILLISECONDS);

    private final FiniteDuration guestFinishCoffeeDuration =
            Duration.create(
                    context().system().settings().config().getDuration(
                            "coffee-house.guest.finish-coffee-duration", MILLISECONDS), MILLISECONDS);

    private final ActorRef barista =
            createBarista();

    private final ActorRef waiter =
            createWaiter();

    private final Map<ActorRef, Integer> guestCaffeineBookkeeper = new HashMap<>();

    private final int caffeineLimit;

    public CoffeeHouse(int caffeineLimit) {
        log().debug("CoffeeHouse Open");
        this.caffeineLimit = caffeineLimit;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().
                match(CreateGuest.class, createGuest -> {
                    final ActorRef guest = createGuest(createGuest.favoriteCoffee);
                    addGuestToBookkeeper(guest);
                }).
                match(ApproveCoffee.class, this::coffeeApproved, approveCoffee ->
                        barista.forward(new Barista.PrepareCoffee(approveCoffee.coffee, approveCoffee.guest), context())
                ).
                match(ApproveCoffee.class, approveCoffee -> {
                    log().info("Sorry, {}, but you have reached your limit.", approveCoffee.guest.path().name());
                    context().stop(approveCoffee.guest);
                }).build();
    }

    public static Props props(int caffeineLimit) {
        return Props.create(CoffeeHouse.class, () -> new CoffeeHouse(caffeineLimit));
    }

    private boolean coffeeApproved(ApproveCoffee approveCoffee) {
        final int guestCaffeineCount = guestCaffeineBookkeeper.get(approveCoffee.guest);
        if (guestCaffeineCount < caffeineLimit) {
            guestCaffeineBookkeeper.put(approveCoffee.guest, guestCaffeineCount + 1);
            return true;
        }
        return false;
    }

    private void addGuestToBookkeeper(ActorRef guest) {
        guestCaffeineBookkeeper.put(guest, 0);
        log().debug("Guest {} added to bookkeeper", guest);
    }

    protected ActorRef createBarista() {
        return context().actorOf(Barista.props(baristaPrepareCoffeeDuration), "barista");
    }

    protected ActorRef createWaiter() {
        return context().actorOf(Waiter.props(self()), "waiter");
    }

    protected ActorRef createGuest(Coffee favoriteCoffee) {
        return context().actorOf(Guest.props(waiter, favoriteCoffee, guestFinishCoffeeDuration));
    }

    public static final class CreateGuest {

        public final Coffee favoriteCoffee;

        public CreateGuest(final Coffee favoriteCoffee) {
            checkNotNull(favoriteCoffee, "Favorite coffee cannot be null");
            this.favoriteCoffee = favoriteCoffee;
        }

        @Override
        public String toString() {
            return "CreateGuest{favoriteCoffee=" + favoriteCoffee + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof CreateGuest) {
                CreateGuest that = (CreateGuest) o;
                return this.favoriteCoffee.equals(that.favoriteCoffee);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h = 1;
            h *= 1000003;
            h ^= favoriteCoffee.hashCode();
            return h;
        }
    }

    public static final class ApproveCoffee {

        public final Coffee coffee;

        public final ActorRef guest;

        public ApproveCoffee(Coffee coffee, ActorRef guest) {
            this.coffee = coffee;
            this.guest = guest;
        }

        @Override
        public String toString() {
            return "ApproveCoffee{" +
                    "coffee=" + coffee +
                    ", guest=" + guest +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ApproveCoffee that = (ApproveCoffee) o;

            if (coffee != null ? !coffee.equals(that.coffee) : that.coffee != null) return false;
            return guest != null ? guest.equals(that.guest) : that.guest == null;
        }

        @Override
        public int hashCode() {
            int result = coffee != null ? coffee.hashCode() : 0;
            result = 31 * result + (guest != null ? guest.hashCode() : 0);
            return result;
        }
    }
}

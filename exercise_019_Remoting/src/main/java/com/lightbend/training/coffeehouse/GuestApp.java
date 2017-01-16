/**
 * Copyright © 2014, 2015 Typesafe, Inc. All rights reserved. [http://www.typesafe.com]
 */
package com.lightbend.training.coffeehouse;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GuestApp implements Terminal {

    public static final Pattern optPattern = Pattern.compile("(\\S+)=(\\S+)");

    private final ActorSystem system;

    private final LoggingAdapter log;

    private final ActorRef hostess;

    public GuestApp(final ActorSystem system) {
        this.system = system;
        log = Logging.getLogger(system, getClass().getName());
        hostess = createHostess();
    }

    public static void main(final String[] args) throws Exception {
        final Map<String, String> opts = argsToOpts(Arrays.asList(args));
        applySystemProperties(opts);
        final String name = opts.getOrDefault("name", "guestapp");

        final ActorSystem system = ActorSystem.create(String.format("%s-system", name), ConfigFactory.load("guest.conf"));
        final GuestApp guestApp = new GuestApp(system);
        guestApp.run();
    }

    public static Map<String, String> argsToOpts(final List<String> args) {
        final Map<String, String> opts = new HashMap<>();
        for (final String arg : args) {
            final Matcher matcher = optPattern.matcher(arg);
            if (matcher.matches()) opts.put(matcher.group(1), matcher.group(2));
        }
        return opts;
    }

    public static void applySystemProperties(final Map<String, String> opts) {
        opts.forEach((key, value) -> {
            if (key.startsWith("-D")) System.setProperty(key.substring(2), value);
        });
    }

    private void run() throws IOException, TimeoutException, InterruptedException {
        log.warning(
                String.format("{} running%nEnter commands into the terminal, e.g. 'q' or 'quit'"),
                getClass().getSimpleName()
        );
        commandLoop();
        Await.ready(system.whenTerminated(), Duration.Inf());
    }

    protected ActorRef createHostess() {
        return system.actorOf(Hostess.props(), "hostess");
    }

    private void commandLoop() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            TerminalCommand tc = Terminal.create(in.readLine());
            if (tc instanceof TerminalCommand.Guest) {
                TerminalCommand.Guest tcg = (TerminalCommand.Guest) tc;
                createGuest(tcg.count, tcg.coffee, tcg.maxCoffeeCount);
            } else if (tc == TerminalCommand.Quit.Instance) {
                system.terminate();
                break;
            } else {
                TerminalCommand.Unknown u = (TerminalCommand.Unknown) tc;
                log.warning("Unknown terminal command {}!", u.command);
            }
        }
    }

    protected void createGuest(int count, Coffee coffee, int maxCoffeeCount) {
        for (int i = 0; i < count; i++) {
            hostess.tell(new Hostess.CreateGuest(coffee, maxCoffeeCount), ActorRef.noSender());
        }
    }
}

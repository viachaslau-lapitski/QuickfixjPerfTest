/*
 * This source file was generated by the Gradle 'init' task
 */
package org.quickfix;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.ClOrdID;
import quickfix.field.HandlInst;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.fix42.NewOrderSingle;

import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class PerformanceTestApp {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestApp.class);
    private static final AtomicInteger messageCounter = new AtomicInteger(0);
    private static final NewOrderSingle newOrderSingle = new NewOrderSingle(
            new ClOrdID(String.valueOf(System.currentTimeMillis())),
            new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PRIVATE_NO_BROKER_INTERVENTION),
            new Symbol("AAPL"),
            new Side(Side.BUY),
            new quickfix.field.TransactTime(),
            new quickfix.field.OrdType(quickfix.field.OrdType.MARKET)
    );

    private static String mode = "client";
    private static int tps = 0;
    private static SessionID session;


    private static void reportProgress() {
        // Periodically report the number of received messages
        Timer reportTimer = new Timer();
        reportTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                logger.info("{}: Messages received: {}",mode, messageCounter.get());
            }
        }, 1000, 1000);
    }

    private static void startLoadGenerator() {
        Timer loadTimer = new Timer();
        loadTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < tps; i++) {
                        Session.sendToTarget(newOrderSingle, session);
                    }
                } catch (SessionNotFound e) {
                    logger.error("exception in startLoadGenerator", e);
                }
            }
        }, 0, 1000);
    }

    // Example usage:
    // java -jar build/libs/YourApp.jar mode=server tps=10
    // or
    // java -jar build/libs/YourApp.jar mode=client tps=10
    //
    // parse arguments
    public static void main(String[] args) throws ConfigError, InterruptedException {
        logger.info("application started");
        parseArgs(args);

        SessionSettings settings = getSettings(mode);
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();
        Application application = getApplication();

        SocketInitiator initiator = null;
        SocketAcceptor acceptor = null;

        try {
            if (mode.equalsIgnoreCase("client")) {
                initiator = new SocketInitiator(application, storeFactory, settings, logFactory, messageFactory);
                initiator.start();
            } else if (mode.equalsIgnoreCase("server")) {
                acceptor = new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
                acceptor.start();
            } else {
                logger.info("Invalid mode. Use 'client' or 'server'.");
                System.exit(1);
            }

            // Keep the application running
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            logger.error("app exception", e);
        } finally {
            if (initiator != null) {
                initiator.stop();
            }
            if (acceptor != null) {
                acceptor.stop();
            }
        }

    }

    private static SessionSettings getSettings(String mode) throws ConfigError {
        String configFile = mode.equalsIgnoreCase("server") ? "quickfix-server.cfg" : "quickfix-client.cfg";
        InputStream inputStream = PerformanceTestApp.class.getResourceAsStream("/" + configFile);
        return new SessionSettings(inputStream);
    }

    private static Application getApplication() {
        return new ApplicationAdapter() {
            @Override
            public void onCreate(SessionID sessionId) {
                session = sessionId;
                logger.info("Session created: {}", sessionId);
            }

            @Override
            public void onLogon(SessionID sessionId) {
                logger.info("Logon: {}", sessionId);
                if (tps > 0) {
                    startLoadGenerator();
                }
                reportProgress();
            }

            @Override
            public void fromApp(Message message, SessionID sessionId) {
                messageCounter.incrementAndGet();
            }
        };
    }

    private static void parseArgs(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("mode=")) {
                mode = arg.split("=",2)[1];
            } else if (arg.startsWith("tps=")) {
                tps = Integer.parseInt(arg.split("=",2)[1]);
            }
        }
        logger.info("app params: mode={}, tps={}", mode, tps);
    }
}

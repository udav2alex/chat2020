package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class Server {
    private Vector<ClientHandler> clients;
    private ExecutorService executorService;
    private AuthService authService;
    private Logger logger;

    public AuthService getAuthService() {
        return authService;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    private Logger loggerSetUp() {
        Logger myLogger = Logger.getLogger("server");

        myLogger.setLevel(Level.ALL);
        myLogger.setUseParentHandlers(false);

        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINER);
        consoleHandler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(LogRecord record) {
                return super.format(record).replaceFirst(System.lineSeparator(), " ");
            }
        });

        myLogger.addHandler(consoleHandler);

        return myLogger;
    }

    public Server() {
        clients = new Vector<>();
        executorService = Executors.newCachedThreadPool();
        authService = new SQLiteAuthService();

        logger = loggerSetUp();

        ServerSocket server = null;
        Socket socket;

        try {
            server = new ServerSocket(8189);
            logger.info("Сервер запустился");

            while (true) {
                socket = server.accept();
                logger.fine("Входящее подключение");
                new ClientHandler(socket, this, logger);
            }

        } catch (IOException e) {
//            e.printStackTrace();
            logger.severe(e.getMessage());
        } finally {
            executorService.shutdown();
            try {
                authService.close();
                server.close();
            } catch (IOException e) {
//                e.printStackTrace();
                logger.severe(e.getMessage());
            }
        }
    }

    public void broadcastMsg(String nick, String msg) {
        for (ClientHandler c : clients) {
            c.sendMsg(nick + " : " + msg);
        }
    }

    public void privateMsg(ClientHandler sender, String receiver, String msg) {
        String message = String.format("[ %s ] private [ %s ] : %s", sender.getNick(), receiver, msg);

        for (ClientHandler c : clients) {
            if (c.getNick().equals(receiver)) {
                c.sendMsg(message);
                if (!sender.getNick().equals(receiver)) {
                    sender.sendMsg(message);
                }
                return;
            }
        }

        sender.sendMsg("not found user :" + receiver);
    }


    public void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
        broadcastClientList();
    }

    public void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastClientList();
    }


    public boolean isLoginAuthorized(String login) {
        for (ClientHandler c : clients) {
            if (c.getLogin().equals(login)) {
                return true;
            }
        }
        return false;
    }

    private void broadcastClientList() {
        StringBuilder sb = new StringBuilder("/clientlist ");

        for (ClientHandler c : clients) {
            sb.append(c.getNick()).append(" ");
        }

        String msg = sb.toString();
        for (ClientHandler c : clients) {
            c.sendMsg(msg);
        }
    }
}

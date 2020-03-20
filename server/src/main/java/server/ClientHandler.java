package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

public class ClientHandler {
    private Socket socket = null;
    private DataInputStream in;
    private DataOutputStream out;
    private Server server;
    private Logger logger;
    private String nick;
    private String login;

    ClientHandler(Socket socket, Server server, Logger logger) {
        try {
            this.socket = socket;
            this.server = server;
            this.logger = logger;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            server.getExecutorService().execute(new Thread(() -> {
                try {
                    socket.setSoTimeout(120000);
                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();
                        if (str.startsWith("/reg ")) {
                            String[] token = str.split(" ");
                            boolean b = server
                                    .getAuthService()
                                    .registration(token[1], token[2], token[3]);
                            if (b) {
                                sendLoggedMsg("Регистрация " + token[1] + " прошла успешно");
                            } else {
                                sendLoggedMsg("Пользователь " + token[1] + " не может быть зарегистрирован");
                            }
                        }

                        if (str.equals("/end")) {
                            throw new RuntimeException("сами ");
                        }

                        if (str.equals("/change")) {
                            String[] token = str.split(" ");
                            if (token.length != 4) {
                                continue;
                            }

                            if(!token[3].equals(server
                                    .getAuthService()
                                    .getNicknameByLoginAndPassword(token[1], token[2]))) {
                                sendLoggedMsg("Неправильные логин(" + token[1] + ")/проль!");
                            }

                            if (server.getAuthService().changeNick(token[1], token[2], token[3])) {
                                sendLoggedMsg("Имя " + nick + " изменено на " + token[3] + "!");
                                nick = token[3];
                                sendLoggedMsg("/authok " + nick);
                            } else {
                                sendLoggedMsg("Изменить имя " + nick + " не удалось!");
                            }
                        }

                        if (str.startsWith("/auth ")) {
                            String[] token = str.split(" ");
                            if (token.length < 3) {
                                continue;
                            }
                            String newNick = server
                                    .getAuthService()
                                    .getNicknameByLoginAndPassword(token[1], token[2]);
                            if (newNick != null) {
                                login = token[1];
                                if (!server.isLoginAuthorized(login)) {
                                    sendLoggedMsg("/authok " + newNick);
                                    nick = newNick;
                                    server.subscribe(this);
                                    socket.setSoTimeout(0);
                                    break;
                                } else {
                                    sendLoggedMsg("С логином " + login + " уже авторизовались");
                                }
                            } else {
                                sendLoggedMsg("Неверный логин(" + token[1] + ") / пароль");
                            }
                        }
                    }

                    //цикл работы
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                out.writeUTF("/end");
                                break;
                            }
                            if (str.startsWith("/w ")) {
                                String[] token = str.split(" ", 3);
                                if (token.length == 3) {
                                    server.privateMsg(this, token[1], token[2]);
                                }
                            }
                        } else {
                            server.broadcastMsg(nick, str);
                        }


                    }
                }catch (SocketTimeoutException e){
                    logger.fine("Клиент отключился по таймауту");
                } catch (RuntimeException e) {
                    logger.fine("Сами вызвали исключение...");
                } catch (IOException e) {
//                    e.printStackTrace();
                    logger.severe(e.getMessage());
                } finally {
                    server.unsubscribe(this);
                    try {
                        in.close();
                    } catch (IOException e) {
//                        e.printStackTrace();
                        logger.severe(e.getMessage());
                    }
                    try {
                        out.close();
                    } catch (IOException e) {
//                        e.printStackTrace();
                        logger.severe(e.getMessage());
                    }
                    try {
                        socket.close();
                    } catch (IOException e) {
//                        e.printStackTrace();
                        logger.severe(e.getMessage());
                    }
                    logger.fine("Клиент отключился");
                }
            }));

        } catch (IOException e) {
//            e.printStackTrace();
            logger.severe(e.getMessage());
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
//            e.printStackTrace();
            logger.severe(e.getMessage());
        }
    }

    private void sendLoggedMsg(String msg) {
        try {
            out.writeUTF(msg);
            logger.info(msg);
        } catch (IOException e) {
//            e.printStackTrace();
            logger.severe(e.getMessage());
        }
    }

    public String getNick() {
        return nick;
    }

    public String getLogin() {
        return login;
    }
}

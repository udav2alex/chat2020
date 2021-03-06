package client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.LinkedList;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    public TextArea textArea;
    @FXML
    public TextField textField;
    @FXML
    public HBox authPanel;
    @FXML
    public HBox msgPanel;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public ListView clientList;

    private boolean changeNick = false;

    Socket socket;
    DataInputStream in;
    DataOutputStream out;

    final String IP_ADDRESS = "localhost";
    final int PORT = 8189;

    BufferedWriter writer = null;
    String logDir = ".\\client\\history";
    String logNamePattern = logDir + "\\history_%s.txt";

    private boolean authenticated;
    private String nickname;
    private String login;

    Stage regStage;

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);
        msgPanel.setVisible(authenticated);
        msgPanel.setManaged(authenticated);
        clientList.setVisible(authenticated);
        clientList.setManaged(authenticated);
        if (!authenticated) {
            nickname = "";
        }
        textArea.clear();
        setTitle("chat 2020");

        try {
            File dir = new File(logDir);
            if (!dir.exists() && dir.mkdirs()) {
                System.out.println("Создана папка для истории переписки!");
            } else if (!dir.exists()) {
                System.out.println("Не могу создать папку для истории переписки!");
            }

            String logPath = String.format(logNamePattern, getLogin());
            File file = new File(logPath);

            if (!file.exists() && file.createNewFile()) {
                System.out.println("Создан новый log-файл " + logPath);
            } else if (!file.exists()) {
                System.out.println("Не могу создать новый log-файл " + logPath);
            } else {
                loadMessages(file);
            }
            writer = new BufferedWriter(new FileWriter(file, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadMessages(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            LinkedList<String> strings = new LinkedList<>();
            String string;

            while ((string = reader.readLine()) != null) {
                strings.add(string);
                if (strings.size() > 100) strings.remove(0);
            }

            for (int i = 0; i < strings.size(); i++) {
                textArea.appendText(strings.get(i));
                textArea.appendText("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        regStage = createRegWindow();
        authenticated = false;
        Platform.runLater(() -> {
            Stage stage = (Stage) textField.getScene().getWindow();
            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent event) {
                    System.out.println("bue");
                    if (socket != null && !socket.isClosed()) {
                        try {
                            out.writeUTF("/end");
                            socket.close();
                            writer.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        });

    }

    public void connect() {
        try {
            socket = new Socket(IP_ADDRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();
                        if (str.startsWith("/authok ")) {
                            setAuthenticated(true);
                            nickname = str.split(" ")[1];
                            break;
                        }
                        textArea.appendText(str + "\n");
                    }

                    setTitle("chat 2020 : " + nickname);

                    //цикл работы
                    while (true) {
                        String str = in.readUTF();
                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                setAuthenticated(false);
                                break;
                            }
                            if (str.startsWith("/clientlist ")) {
                                String[] token = str.split(" ");
                                Platform.runLater(() -> {
                                    clientList.getItems().clear();
                                    for (int i = 1; i < token.length; i++) {
                                        clientList.getItems().add(token[i]);
                                    }
                                });
                            }

                        } else {
                            textArea.appendText(str + "\n");

                            try {
                                writer.write(str);
                                writer.write(System.lineSeparator());
                                writer.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (SocketException e) {
                    System.out.println("Сервер отключился");
                    setAuthenticated(false);
                } catch (IOException e) {
//                    e.printStackTrace();
                    System.out.println("Соединение с сервером разорвано");
                } finally {
                    try {
                        socket.close();
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg() {
        if (textField.getText().trim().length() > 0) {
            try {
                out.writeUTF(textField.getText());
                textField.clear();
                textField.requestFocus();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void tryToAuth(ActionEvent actionEvent) {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            out.writeUTF("/auth " + loginField.getText() + " " + passwordField.getText());
            login = loginField.getText();
            passwordField.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void setTitle(String title) {
        Platform.runLater(() -> {
            ((Stage) textField.getScene().getWindow()).setTitle(title);
        });
    }

    public void clickClientList(MouseEvent mouseEvent) {
//        System.out.println(clientList.getSelectionModel().getSelectedItem());
        String receiver = clientList.getSelectionModel().getSelectedItem().toString();
        textField.setText("/w " + receiver + " ");

    }

    public void registration(ActionEvent actionEvent) {
        regStage.show();
    }

    private Stage createRegWindow() {
        Stage stage = null;
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/reg.fxml"));
            Parent root1 = fxmlLoader.load();
            stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);

            RegController regController = fxmlLoader.getController();
            regController.controller = this;

            stage.setTitle("registration");
            stage.setScene(new Scene(root1, 300, 200));

        } catch (IOException e) {
            e.printStackTrace();
        }
        return stage;
    }

    public void tryRegister(String login, String password, String nickname) {
        changeNick = false;
        String msg = String.format("/reg %s %s %s", login, password, nickname);

        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getLogin() {
        return login;
    }
}

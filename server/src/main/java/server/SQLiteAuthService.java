package server;

import java.sql.*;

public class SQLiteAuthService implements AuthService {
    private static final String DB_PATH = "jdbc:sqlite:db.sqlite";
    private Connection connection;
    private PreparedStatement registerStatement;
    private PreparedStatement findNicknameByLoginStatement;
    private PreparedStatement changeNickStatement;

    public SQLiteAuthService() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_PATH);

            registerStatement = connection.prepareStatement(
                    "INSERT INTO users (login, password, nickname) VALUES (?, ?, ?);");
            findNicknameByLoginStatement = connection.prepareStatement(
                    "SELECT nickname FROM users WHERE login = ? AND password = ?;");
            changeNickStatement = connection.prepareStatement(
                    "UPDATE users SET nickname = ? WHERE login = ? AND password = ?;");

        } catch (ClassNotFoundException e) {
            System.out.println("Не найден класс JDBC-драйвера...");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Не найден файл БД...");
            e.printStackTrace();
        }
    }

    @Override
    public String getNicknameByLoginAndPassword(String login, String password) {
        try {
            findNicknameByLoginStatement.setString(1, login);
            findNicknameByLoginStatement.setString(2, password);
            ResultSet resultSet = findNicknameByLoginStatement.executeQuery();
            while (resultSet.next()) {
                return resultSet.getString(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            System.out.println("Не нашли nickname для указанных login & password!");
        }

        return null;
    }

    @Override
    public boolean registration(String login, String password, String nickname) {
        try {
            registerStatement.setString(1, login);
            registerStatement.setString(2, password);
            registerStatement.setString(3, nickname);
            if (registerStatement.executeUpdate() == 1) {
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при регистрации!");
        }
        return false;
    }

    @Override
    public boolean changeNick(String login, String password, String nickname) {
        try {
            changeNickStatement.setString(1, nickname);
            changeNickStatement.setString(2, login);
            changeNickStatement.setString(3, password);
            if (changeNickStatement.executeUpdate() > 0) {
                return true;
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при изменении имени!");
        }
        return false;
    }

    @Override
    public void close() {
        try {
            registerStatement.close();
            findNicknameByLoginStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("Сервер аутентификации закрыт");
    }
}

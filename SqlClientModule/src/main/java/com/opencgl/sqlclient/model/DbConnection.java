package com.opencgl.sqlclient.model;

import javafx.beans.property.*;

public class DbConnection {
    public enum DbType { SQLITE, MYSQL, MARIADB, POSTGRESQL, ORACLE, SQL_SERVER, H2 }
    
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<DbType> type = new SimpleObjectProperty<>();
    private final StringProperty host = new SimpleStringProperty("localhost");
    private final IntegerProperty port = new SimpleIntegerProperty(3306);
    private final StringProperty database = new SimpleStringProperty();
    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty password = new SimpleStringProperty();
    private final StringProperty url = new SimpleStringProperty();
    
    public DbConnection() {}
    
    public DbConnection(String name, DbType type) {
        this.name.set(name);
        this.type.set(type);
        updateDefaultPort();
    }
    
    private void updateDefaultPort() {
        switch (type.get()) {
            case MYSQL -> port.set(3306);
            case MARIADB -> port.set(3306);
            case POSTGRESQL -> port.set(5432);
            case ORACLE -> port.set(1521);
            case SQL_SERVER -> port.set(1433);
            case H2 -> port.set(9092);
            case SQLITE -> port.set(0);
        }
    }
    
    public String buildJdbcUrl() {
        switch (type.get()) {
            case SQLITE:
                return "jdbc:sqlite:" + database.get();
            case MYSQL:
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                    host.get(), port.get(), database.get());
            case MARIADB:
                return String.format("jdbc:mariadb://%s:%d/%s?useSsl=false",
                    host.get(), port.get(), database.get());
            case POSTGRESQL:
                return String.format("jdbc:postgresql://%s:%d/%s",
                    host.get(), port.get(), database.get());
            case ORACLE:
                return String.format("jdbc:oracle:thin:@%s:%d:%s",
                    host.get(), port.get(), database.get());
            case SQL_SERVER:
                return String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
                    host.get(), port.get(), database.get());
            case H2:
                return "jdbc:h2:" + database.get();
            default:
                return "";
        }
    }
    
    // Getters and Setters
    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }
    public StringProperty nameProperty() { return name; }
    
    public DbType getType() { return type.get(); }
    public void setType(DbType value) { 
        type.set(value);
        updateDefaultPort();
    }
    public ObjectProperty<DbType> typeProperty() { return type; }
    
    public String getHost() { return host.get(); }
    public void setHost(String value) { host.set(value); }
    public StringProperty hostProperty() { return host; }
    
    public int getPort() { return port.get(); }
    public void setPort(int value) { port.set(value); }
    public IntegerProperty portProperty() { return port; }
    
    public String getDatabase() { return database.get(); }
    public void setDatabase(String value) { database.set(value); }
    public StringProperty databaseProperty() { return database; }
    
    public String getUsername() { return username.get(); }
    public void setUsername(String value) { username.set(value); }
    public StringProperty usernameProperty() { return username; }
    
    public String getPassword() { return password.get(); }
    public void setPassword(String value) { password.set(value); }
    public StringProperty passwordProperty() { return password; }
    
    public String getUrl() { return url.get(); }
    public void setUrl(String value) { url.set(value); }
    public StringProperty urlProperty() { return url; }
}

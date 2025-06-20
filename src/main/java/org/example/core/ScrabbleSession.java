package org.example.core;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.paint.Color;
import org.example.controller.BoardController;
import org.example.controller.StatusController;
import org.example.controller.TurnController;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ScrabbleSession implements Runnable{
    private static final String END_GAME_TEXT = "Player %s has won!";

    private final Alert endGameAlert = new Alert(Alert.AlertType.INFORMATION);
    private final Alert fullAlert = new Alert(Alert.AlertType.WARNING);
    private final Socket socket;
    private final ObjectOutputStream out;
    private final String username;

    private StatusController statusController;
    private BoardController boardController;
    private TurnController turnController;

    private ScrabbleSession(String username, Socket socket) throws IOException {
        this.socket = socket;
        this.username = username;
        out = new ObjectOutputStream(socket.getOutputStream());

        endGameAlert.setHeaderText("Game Over!");
        endGameAlert.setTitle("Game Over!");
        endGameAlert.setOnCloseRequest(event -> System.exit(0));

        fullAlert.setTitle("Game in progress!");
        fullAlert.setHeaderText("This lobby currently has a game in progress!");
        fullAlert.setContentText("Try joining later or choose another lobby");
        fullAlert.setOnCloseRequest(event -> System.exit(0));
    }

    public void setStatusController(StatusController statusController) {
        statusController.setSession(this);
        statusController.addPlayer(username);
        this.statusController = statusController;
    }

    public void setBoardController(BoardController boardController) {
        this.boardController = boardController;
    }

    public void setTurnController(TurnController turnController) {
        turnController.setSession(this);
        this.turnController = turnController;
    }

    public void attemptPlace(String word, int row, char col, boolean vertical) throws IOException {
        Platform.runLater(() -> turnController.setVisibility(false));

        String move = "PLACE %s %s %d %s".formatted(word, col, row, vertical ? 'V' : 'H');
        out.writeObject(move);
        out.flush();
    }

    public void skip() throws IOException {
        Platform.runLater(() -> turnController.setVisibility(false));
        out.writeObject("SKIP");
        out.flush();
    }

    public void startGame() throws IOException {
        Platform.runLater(() -> statusController.hideStart());
        out.writeObject("SCRABBLE");
        out.flush();
    }

    public static ScrabbleSession login(String username, String roomID, String hostname, int port) throws IOException {
        Socket socket = new Socket(hostname, port);
        ScrabbleSession game = new ScrabbleSession(username, socket);

        game.out.writeObject(new ClientConnectionDTO(username, roomID));
        game.out.flush();

        return game;
    }

    @Override
    public void run() {
        try(ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            while (!socket.isClosed()) {
                String response = (String) in.readObject();
                System.out.println(response);

                if(response.equals("START")){
                    Platform.runLater(() -> statusController.hideStart());
                }
                else if(response.startsWith("TURN")) {
                    Platform.runLater(() -> {
                        statusController.setStatus("%s's turn".formatted(response.split(" ")[1]));

                        if(response.contains(username))
                            turnController.setVisibility(true);
                    });
                }
                else if(response.startsWith("RACK")){
                    Platform.runLater(() -> statusController.clearLetters());

                    String rack = response.split(" ")[1];
                    for(char letter : rack.toCharArray()){
                        Platform.runLater(() -> statusController.addLetter(letter));
                    }
                }
                else if(response.equals("INVALID")) {
                    Platform.runLater(() -> {
                        statusController.setStatus("Invalid move! Try again!", Color.RED);
                        turnController.setVisibility(true);
                    });

                }
                else if(response.startsWith("PLACE")) {
                    String[] move = response.split(" ");
                    Platform.runLater(() -> boardController.placeWord(move[1], Integer.parseInt(move[3]), move[2].charAt(0), move[4].charAt(0) == 'V'));
                }
                else if (response.startsWith("PLAYERS")) {
                    String[] players = response.split(" ");
                    for(int i = 1; i < players.length; i++){
                        String nick = players[i];
                        Platform.runLater(() -> statusController.addPlayer(nick));
                    }
                }
                else if (response.startsWith("JOIN")) {
                    Platform.runLater(() -> statusController.addPlayer(response.split(" ")[1]));
                }

                else if (response.startsWith("SCORES")) {
                    Platform.runLater(() -> statusController.setScores(response));
                }
                else if (response.startsWith("GAME_END")) {
                    String[] end = response.split(" ");
                    endGameAlert.setContentText(END_GAME_TEXT.formatted(end[1]));
                    Platform.runLater(endGameAlert::showAndWait);
                }
                else if (response.equals("FULL")) {
                    Platform.runLater(fullAlert::showAndWait);
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("Disconnected from server.");
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

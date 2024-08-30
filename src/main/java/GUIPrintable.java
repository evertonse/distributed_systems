import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GUIPrintable {
    private JFrame frame;
    private JTextArea chatHistory;
    private JTextField inputPrompt;
    private StringBuilder history;


    public GUIPrintable() {
        history = new StringBuilder();
        createAndShowGUI();
    }


    private void createAndShowGUI() {
        frame = new JFrame("Chat Window");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        JPanel panel = new JPanel(new BorderLayout());


        chatHistory = new JTextArea();
        chatHistory.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(chatHistory);
        panel.add(scrollPane, BorderLayout.CENTER);

        inputPrompt = new JTextField();
        inputPrompt.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = inputPrompt.getText();
                appendToChatHistory("You: " + input);
                inputPrompt.setText("");
            }
        });
        panel.add(inputPrompt, BorderLayout.SOUTH);

        frame.add(panel);
        frame.setVisible(true);
    }

    private void appendToChatHistory(String message) {
        history.append(message).append("\n");
        chatHistory.setText(history.toString());
    }

    public void print(String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                appendToChatHistory(text);
            }
        });

    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new GUIPrintable();

            }
        });
    }
}

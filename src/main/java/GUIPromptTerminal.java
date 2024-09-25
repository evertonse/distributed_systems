import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

// import javafx.application.Platform;
import java.util.concurrent.CountDownLatch;



import javax.swing.*;
import java.awt.*;

import java.awt.event.*;
import java.util.concurrent.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.regex.Pattern;


public class GUIPromptTerminal extends PromptTerminal {

  private JFrame frame;
  private JLabel promptLabel;
  private JTextArea chatHistory;
  private JTextArea promptArea; // supposed to be not readable
  private JTextField inputPrompt;
  private StringBuffer history = new StringBuffer();

  private BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

  boolean cookMode = false;


  public GUIPromptTerminal(CompletionProvider completionProvider) {
    super(completionProvider);
    createAndShowGUI();
  }

  @Override
  public void setPrompt(String newPrompt) {
    this.prompt = newPrompt;
    this.promptLabel.setText(this.prompt);
  }


  @Override
  public void moveCursorUp(StringBuilder sb, int rows) {}
  @Override
  public void moveToStartAndClear(StringBuilder sb) {}
  @Override
  public void moveCursorToLastRow(StringBuilder sb) { clear(); }

  private int getStartIndexOfWordUnderCursor() {
    String user = inputPrompt.getText();

    boolean earlyStop = false;
    String ignore = "!/.-#@";
    int startPosition = cursorPosition();
    while (!earlyStop && cursorIsInBounds(startPosition, -1) &&
           !isDelimiter(user.charAt(startPosition - 1), ignore)) {
      startPosition -= 1;
    }
    startPosition = clamp(startPosition, 0, inputPrompt.getText().length());

    return startPosition;
  }

  private boolean cursorIsInBounds(int cursorPosition, int i) {
    if (cursorPosition + i > inputPrompt.getText().length() - 1) {
      return false;
    } else if (cursorPosition + i < 0) {
      return false;
    }
    return true;
  }

  int cursorPosition() {
    return inputPrompt.getCaretPosition();
  }

  private CompletionStorage handleAutoCompletion(CompletionStorage _cs, int val) {
    CompletionStorage cs = _cs;
    cs.lastUsed = true;
    StringBuffer editable = new StringBuffer(inputPrompt.getText());
    if (
       cs.previousWord == null
       || (cs.possibilities != null && cs.possibilities.size() <= 1)
    ) {
      cs.startWordIndex = getStartIndexOfWordUnderCursor();
      cs.previousWord   = editable.substring(cs.startWordIndex, cursorPosition());
    }

      if (editable.toString().endsWith(FileUtils.separator)) {
        cs.modifyEnter = true;
      }

    // We garanteed no null pointer accessing because we check for nullness
    // before accessing size method using short circuiting
    if (completionEnabled &&
        (cs.possibilities == null || cs.possibilities.size() <= 1)) {
      CompletionResult cr = completionProvider.getCompletionPossibilities(
          editable.toString(), cs.previousWord);
      cs.modifyEnter = cr.modifyEnter;
      cs.possibilities = cr.possibilities;
    }

    if (cs.possibilities != null && cs.possibilities.size() > 0) {
      cs.index = properMod(cs.index + val, cs.possibilities.size());
      String completion = cs.possibilities.get(cs.index);

      editable.delete(cs.startWordIndex, cursorPosition());
      editable.insert(cs.startWordIndex, completion);

      int newCursor = cs.startWordIndex + completion.length();
      editable.setLength(newCursor);

      if (editable.toString().endsWith(FileUtils.separator)) {
        cs.modifyEnter = true;
      }

      inputPrompt.setText(editable.toString());
      inputPrompt.setCaretPosition(cursorSnapToInBounds(newCursor));
    }
    return cs;
  }

  private int cursorSnapToInBounds(int cursorPosition) {
    StringBuffer editable = new StringBuffer(inputPrompt.getText());

    if (cursorPosition > editable.length() - 1) {
      cursorPosition = editable.length();
    }

    else if (cursorPosition < 0) {
      cursorPosition = 0;
    }
    return cursorPosition;
  }

  private int cursorSnapToInBounds(int cursorPosition, String editable) {

    if (cursorPosition > editable.length() - 1) {
      cursorPosition = editable.length();
    }

    else if (cursorPosition < 0) {
      cursorPosition = 0;
    }
    return cursorPosition;
  }


  CompletionStorage cs = new CompletionStorage();

  private void deleteWord() {
    StringBuffer editable  = new StringBuffer(inputPrompt.getText());
    String user = editable.toString();

    boolean earlyStop = false;
    int cursor = cursorPosition();
    while (cursorIsInBounds(cursor, -1) &&
           isDelimiter(user.charAt(cursor - 1))) {
      cursor -= 1;
      earlyStop = true;
      editable.deleteCharAt(cursor);
    }
    while (!earlyStop && cursorIsInBounds(cursor, -1) &&
           !isDelimiter(user.charAt(cursor - 1))) {
      cursor -= 1;
      editable.deleteCharAt(cursor);
    }
    cursor = cursorSnapToInBounds(cursor);
    inputPrompt.setCaretPosition(cursor);
    inputPrompt.setText(editable.toString());
  }

  private void handleTabPress(int i) {

    cs = handleAutoCompletion(cs, i);
    String currentInput = inputPrompt.getText();
    // Here you can implement your custom behavior for Tab press
    // For example, you might want to trigger auto-completion
    System.out.print("EnableCompletion:");
    System.out.println(this.completionEnabled);

    System.out.print("CS:");
    System.out.println(cs);

    System.out.println("Tab pressed. Current input: " + currentInput);

  }

  private void createAndShowGUI() {
    int FONT_SIZE = 21;
    frame = new JFrame("Chat Window");

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    frame.setSize(900, 720);
    frame.setPreferredSize(new Dimension(900, 720));

    JPanel panel = new JPanel(new BorderLayout());


    chatHistory = new JTextArea();
    chatHistory.setEditable(false);
    chatHistory.setFont(new Font("Arial", Font.PLAIN, 21));

    JScrollPane scrollPane = new JScrollPane(chatHistory);
    panel.add(scrollPane, BorderLayout.CENTER);

    JPanel inputPanel = new JPanel(new BorderLayout());

    inputPrompt = new JTextField();
    inputPrompt.setFont(new Font("Arial", Font.PLAIN, FONT_SIZE));

    {
      inputPrompt.setFocusTraversalKeysEnabled(false);
      inputPrompt.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (!cs.modifyEnter) {
            String input = inputPrompt.getText();

            if (input == null || input.equals("")) {
              return;
            }

            PROMPT_HISTORY.add(input);
            historyPosition = PROMPT_HISTORY.size();

            inputQueue.offer(input);
            inputPrompt.setText("");
            appendToChatHistory(prompt + input);
          } else {
              cs.reset();
          }
        }
      });

      // Set up Key Bindings for Tab
      // InputMap inputMap = inputPrompt.getInputMap(JComponent.WHEN_FOCUSED);
      // ActionMap actionMap = inputPrompt.getActionMap();

      // inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "handleTab");
      // actionMap.put("handleTab", new AbstractAction() {
      //     @Override
      //     public void actionPerformed(ActionEvent e) {
      //
      //         handleTabPress();
      //
      //     }
      // });

      inputPrompt.addKeyListener(new KeyListener() {
        @Override
        public void keyTyped(KeyEvent e) {
            // Not used
        }

        @Override
        public void keyPressed(KeyEvent e) {
            // Check if the pressed key is not Tab or Shift+Tab
          if (!e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_TAB){
              handleTabPress(1);
          } else if (e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_TAB) {
              handleTabPress(-1);
          } else {
            if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_W) {
              deleteWord();
            } else if (e.getKeyCode() == KeyEvent.VK_UP) { // Up arrow
              if (PROMPT_HISTORY.size() > 0) {
                historyPosition -= 1;
                historyPosition =
                    clamp(historyPosition, 0, PROMPT_HISTORY.size() - 1);
                inputPrompt.setText(PROMPT_HISTORY.get(historyPosition));
              }
            } else if (e.getKeyCode() == KeyEvent.VK_DOWN) { // Down arrow
              if (PROMPT_HISTORY.size() > 0) {
                historyPosition += 1;
                historyPosition =
                    clamp(historyPosition, 0, PROMPT_HISTORY.size() - 1);
                inputPrompt.setText(PROMPT_HISTORY.get(historyPosition));
              }
            }

            System.out.println("Key pressed: " + KeyEvent.getKeyText(e.getKeyCode()));
            if (!e.isShiftDown() && !e.isControlDown() 
                && !(e.getKeyCode() == KeyEvent.VK_ENTER)
            ) {
              cs.lastUsed = false;
            }
          }

          if (!cs.lastUsed || (cs.previousWord == null && cs.possibilities != null && cs.possibilities.size() == 1)) {
            cs.reset();
          }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            // Not used
        }
      });
    }

    promptLabel = new JLabel(this.prompt);
    promptLabel.setPreferredSize(new Dimension(120, 35));
    promptLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, FONT_SIZE+2));
    promptLabel.setHorizontalAlignment(SwingConstants.CENTER);
    promptLabel.setVerticalAlignment(SwingConstants.CENTER);
    promptLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

    inputPanel.add(promptLabel, BorderLayout.WEST);
    inputPanel.add(inputPrompt, BorderLayout.CENTER);

    // inputPrompt.addKeyListener(kl);

    // panel.add(inputPrompt, BorderLayout.SOUTH);
    panel.add(inputPanel, BorderLayout.SOUTH);

    // Center the window on the screen
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.add(panel);
    frame.setVisible(true);
  }

  synchronized  private void appendToChatHistory(String message) {
      history.append(message).append("\n");
      chatHistory.setText(history.toString());
  }

  @Override
  public void print(String text) {
      // If it contains \r then we remove this the current line and add it
      SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
              String cleanedText = removeAnsiEscapeCodes(text);
              appendToChatHistory(cleanedText);
          }
      });
  }

    @Override
    public boolean shouldQuit() { return false; }

    @Override
    public void toCookedMode() {
        this.cookMode = true;
    }

    @Override
    public void clear() {
      SwingUtilities.invokeLater(() -> {
            chatHistory.setText("");
            history.setLength(0);
        });
    }


    @Override
    public void toRawMode() { }

    @Override
    public String readLine() {
      cs = new CompletionStorage();

      try {
          return inputQueue.take().trim();
      } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
      }
    }

    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");
    private String removeAnsiEscapeCodes(String input) {
      // String res = ANSI_ESCAPE_PATTERN.matcher(input).replaceAll("");
      // Remove all trailing newlines and starting
      String res = input.replaceAll("^[\n\r]", "").replaceAll("[\n\r]$", "");
      return res;
    }



    // var kl = new KeyListener() {
    //     @Override
    //     public void keyTyped(KeyEvent e) {
    //         char keyChar = e.getKeyChar();
    //         chatHistory.append("Key Typed: " + keyChar + "\n");
    //     }
    //
    //     @Override
    //     public void keyPressed(KeyEvent e) {
    //         int keyCode = e.getKeyCode();
    //         chatHistory.append("Key Pressed: " + KeyEvent.getKeyText(keyCode) + "\n");
    //     }
    //
    //     @Override
    //     public void keyReleased(KeyEvent e) {
    //         int keyCode = e.getKeyCode();
    //         chatHistory.append("Key Released: " + KeyEvent.getKeyText(keyCode) + "\n");
    //     }
    // };
}


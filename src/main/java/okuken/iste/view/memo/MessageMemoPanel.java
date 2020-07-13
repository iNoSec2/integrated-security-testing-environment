package okuken.iste.view.memo;

import javax.swing.JPanel;

import java.awt.BorderLayout;

import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;

import okuken.iste.controller.Controller;
import okuken.iste.dto.MessageDto;
import okuken.iste.util.UiUtil;
import javax.swing.JScrollPane;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class MessageMemoPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private JTextArea textArea;

	private MessageDto currentMessageDto;
	private UndoManager undoManager;

	public MessageMemoPanel() {
		setLayout(new BorderLayout(0, 0));
		
		textArea = new JTextArea();
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				setMemoChanged();
			}
			@Override
			public void insertUpdate(DocumentEvent e) {
				setMemoChanged();
			}
			@Override
			public void changedUpdate(DocumentEvent e) {
				setMemoChanged();
			}
			private void setMemoChanged() {
				if(currentMessageDto != null) {
					currentMessageDto.setMemoChanged(true);
				}
			}
		});
		textArea.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) { //[CAUTION] It's called after JTable#changeSelection.
				if(currentMessageDto.isMemoChanged()) {
					currentMessageDto.setMemo(textArea.getText());
					Controller.getInstance().saveMessageMemo(currentMessageDto);
				}
			}
		});
		textArea.setRows(10);
		textArea.setTabSize(4);
//		textArea.setLineWrap(true);
		undoManager = UiUtil.addUndoRedoFeature(textArea);
		disablePanel();
		
		JScrollPane scrollPane = new JScrollPane(textArea);
		add(scrollPane, BorderLayout.CENTER);
		
		Controller.getInstance().setMessageMemoPanel(this);
	}

	public void disablePanel() {
		currentMessageDto = null;
		textArea.setText("");
		undoManager.discardAllEdits();
		textArea.setEnabled(false);
	}

	public void enablePanel(MessageDto messageDto) {
		if(currentMessageDto != null && currentMessageDto.isMemoChanged()) { //[CAUTION] This considers case focus out by jtable row selection.
			currentMessageDto.setMemo(textArea.getText());
			Controller.getInstance().saveMessageMemo(currentMessageDto);
		}

		currentMessageDto = messageDto;
		textArea.setText(messageDto.getMemo());
		currentMessageDto.setMemoChanged(false); // clear flag, because setText for initialize textArea set flag on...
		undoManager.discardAllEdits();
		textArea.setEnabled(true);
	}

}
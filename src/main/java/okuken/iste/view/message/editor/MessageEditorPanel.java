package okuken.iste.view.message.editor;

import java.awt.BorderLayout;
import java.util.Optional;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import burp.IHttpRequestResponse;
import burp.IHttpService;
import burp.IMessageEditor;
import burp.IMessageEditorController;
import okuken.iste.controller.Controller;
import okuken.iste.dto.MessageDto;
import okuken.iste.util.BurpUtil;

public class MessageEditorPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private IMessageEditor requestMessageEditor;
	private IMessageEditor responseMessageEditor;

	public MessageEditorPanel() {
		this(createDefaultMessageEditorController(), false, false);
	}

	public MessageEditorPanel(IMessageEditorController messageEditorController, boolean requestEditable, boolean responseEditable) {
		setLayout(new BorderLayout(0, 0));

		requestMessageEditor = BurpUtil.getCallbacks().createMessageEditor(messageEditorController, requestEditable);
		responseMessageEditor = BurpUtil.getCallbacks().createMessageEditor(messageEditorController, responseEditable);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				requestMessageEditor.getComponent(),
				responseMessageEditor.getComponent()
			);
		splitPane.setResizeWeight(0.5);

		add(splitPane);
	}

	private static IMessageEditorController createDefaultMessageEditorController() {
		return new IMessageEditorController() {
			@Override
			public IHttpService getHttpService() {
				return Controller.getInstance().getSelectedMessage().getMessage().getHttpService();
			}
			@Override
			public byte[] getRequest() {
				return Controller.getInstance().getSelectedMessage().getMessage().getRequest();
			}
			@Override
			public byte[] getResponse() {
				return Controller.getInstance().getSelectedMessage().getMessage().getResponse();
			}
		};
	}

	public byte[] getRequest() {
		return requestMessageEditor.getMessage();
	}

	public byte[] getResponse() {
		return responseMessageEditor.getMessage();
	}

	public void setMessage(MessageDto dto) {
		requestMessageEditor.setMessage(
				dto.getMessage().getRequest(),
				true);
		responseMessageEditor.setMessage(
				Optional.ofNullable(dto.getMessage().getResponse()).orElse(new byte[] {}),
				false);
	}

	public void setMessage(IHttpRequestResponse message) {
		setRequest(message.getRequest());
		setResponse(Optional.ofNullable(message.getResponse()).orElse(new byte[] {}));
	}

	public void clearMessage() {
		requestMessageEditor.setMessage(new byte[] {}, true);
		responseMessageEditor.setMessage(new byte[] {}, false);
	}

	public void setRequest(byte[] request) {
		requestMessageEditor.setMessage(request, true);
	}

	public void setResponse(byte[] response) {
		responseMessageEditor.setMessage(response, false);
	}

	public void clearResponse() {
		responseMessageEditor.setMessage(new byte[] {}, false);
	}

}
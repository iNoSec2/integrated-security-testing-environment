package okuken.iste.controller;

import java.io.File;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;

import burp.IContextMenuFactory;
import burp.IHttpRequestResponse;
import burp.ITab;
import okuken.iste.DatabaseManager;
import okuken.iste.dto.AuthAccountDto;
import okuken.iste.dto.AuthConfigDto;
import okuken.iste.dto.MessageChainDto;
import okuken.iste.dto.MessageDto;
import okuken.iste.dto.MessageFilterDto;
import okuken.iste.dto.MessageRepeatDto;
import okuken.iste.dto.MessageRepeatRedirectDto;
import okuken.iste.dto.PayloadDto;
import okuken.iste.dto.ProjectMemoDto;
import okuken.iste.logic.AuthLogic;
import okuken.iste.logic.ConfigLogic;
import okuken.iste.logic.ExportLogic;
import okuken.iste.logic.MemoLogic;
import okuken.iste.logic.MessageChainLogic;
import okuken.iste.logic.MessageFilterLogic;
import okuken.iste.logic.MessageLogic;
import okuken.iste.logic.ProjectLogic;
import okuken.iste.logic.RepeaterLogic;
import okuken.iste.plugin.PluginInfo;
import okuken.iste.plugin.PluginManager;
import okuken.iste.util.BurpUtil;
import okuken.iste.view.SuitePanel;
import okuken.iste.view.SuiteTab;
import okuken.iste.view.auth.AuthPanel;
import okuken.iste.view.header.MainHeaderPanel;
import okuken.iste.view.memo.MessageMemoPanel;
import okuken.iste.view.memo.ProjectMemoPanel;
import okuken.iste.view.message.editor.MessageEditorPanel;
import okuken.iste.view.message.table.MessageTableColumn;
import okuken.iste.view.message.table.MessageTableModel;
import okuken.iste.view.message.table.MessageTablePanel;
import okuken.iste.view.message.table.MessageTablePopupMenu;
import okuken.iste.view.plugin.PluginsPanel;
import okuken.iste.view.repeater.RepeatMasterPanel;
import okuken.iste.view.repeater.RepeaterPanel;

public class Controller {

	private static final Controller instance = new Controller();

	private SuiteTab suiteTab;
	private JTabbedPane mainTabbedPane;
	private JPanel mainPanel;
	private MainHeaderPanel mainHeaderPanel;

	private MessageTablePanel messageTablePanel;
	private MessageTableModel messageTableModel;
	private JTable messageTable;

	private JTabbedPane messageDetailTabbedPane;
	private MessageEditorPanel orgMessageEditorPanel;
	private RepeatMasterPanel repeatMasterPanel;
	private RepeaterPanel repeaterPanel;

	private MessageMemoPanel messageMemoPanel;

	private ProjectMemoPanel projectMemoPanel;

	private AuthPanel authPanel;

	private PluginsPanel pluginsPanel;

	private Controller() {}
	public static Controller getInstance() {
		return instance;
	}
	
	public void setSuiteTab(SuiteTab suiteTab) {
		this.suiteTab = suiteTab;
	}
	public void setMainTabbedPane(JTabbedPane mainTabbedPane) {
		this.mainTabbedPane = mainTabbedPane;
	}
	public JTabbedPane getMainTabbedPane() {
		return mainTabbedPane;
	}
	public void setMainPanel(JPanel mainPanel) {
		this.mainPanel = mainPanel;
	}
	public JPanel getMainPanel() {
		return this.mainPanel;
	}
	public void setMainHeaderPanel(MainHeaderPanel mainHeaderPanel) {
		this.mainHeaderPanel = mainHeaderPanel;
	}
	public void setMessageTablePanel(MessageTablePanel messageTablePanel) {
		this.messageTablePanel = messageTablePanel;
	}
	public void setMessageTableModel(MessageTableModel messageTableModel) {
		this.messageTableModel = messageTableModel;
	}
	public void setMessageTable(JTable messageTable) {
		this.messageTable = messageTable;
	}
	public void setMessageDetailTabbedPane(JTabbedPane messageDetailTabbedPane) {
		this.messageDetailTabbedPane = messageDetailTabbedPane;
	}
	public JTabbedPane getMessageDetailTabbedPane() {
		return messageDetailTabbedPane;
	}
	public void setOrgMessageEditorPanel(MessageEditorPanel orgMessageEditorPanel) {
		this.orgMessageEditorPanel = orgMessageEditorPanel;
	}
	public void setRepeatMasterPanel(RepeatMasterPanel repeatMasterPanel) {
		this.repeatMasterPanel = repeatMasterPanel;
	}
	public void setRepeaterPanel(RepeaterPanel repeaterPanel) {
		this.repeaterPanel = repeaterPanel;
	}
	public void setMessageMemoPanel(MessageMemoPanel messageMemoPanel) {
		this.messageMemoPanel = messageMemoPanel;
	}
	public void setProjectMemoPanel(ProjectMemoPanel projectMemoPanel) {
		this.projectMemoPanel = projectMemoPanel;
	}
	public void setAuthPanel(AuthPanel authPanel) {
		this.authPanel = authPanel;
	}
	public void setPluginsPanel(PluginsPanel pluginsPanel) {
		this.pluginsPanel = pluginsPanel;
	}


	public void sendMessagesToSuiteTab(List<IHttpRequestResponse> messages) {
		BurpUtil.highlightTab(suiteTab);
		List<MessageDto> messageDtos = messages.stream()
				.map(message -> MessageLogic.getInstance().convertHttpRequestResponseToDto(message))
				.collect(Collectors.toList());
		MessageLogic.getInstance().saveMessages(messageDtos);
		this.messageTableModel.addRows(messageDtos);
		MessageLogic.getInstance().saveMessageOrder(this.messageTableModel.getRows()); // TODO: join transaction...
		messageDtos.forEach(messageDto -> MemoLogic.getInstance().saveMessageMemo(messageDto));

		applyMessageFilter();
		refreshComponentsDependentOnMessages(this.messageTableModel.getRows());
	}

	public void deleteMessages() {
		var selectedRowIndexs = getSelectedRowIndexs();
		Collections.reverse(selectedRowIndexs);

		for(var rowIndex: selectedRowIndexs) {
			var dto = messageTableModel.removeRow(rowIndex);
			MessageLogic.getInstance().deleteMessage(dto);
		}
		MessageLogic.getInstance().saveMessageOrder(this.messageTableModel.getRows()); // TODO: join transaction...

		applyMessageFilter();
		refreshComponentsDependentOnMessages(this.messageTableModel.getRows());
	}

	private void refreshComponentsDependentOnMessages(List<MessageDto> messageDtos) {
		authPanel.refreshConfigPanel(messageDtos);
	}

	public void initSizeRatioOfParts() {
		initMessageTableColumnWidth();
		((SuitePanel)suiteTab.getUiComponent()).initDividerLocation();
	}
	private void initMessageTableColumnWidth() {
		Enumeration<TableColumn> e = messageTable.getColumnModel().getColumns();
		for (int i = 0; e.hasMoreElements(); i++) {
			e.nextElement().setPreferredWidth(MessageTableColumn.getByCaption(messageTable.getColumnName(i)).getWidth());
		}
	}

	public List<Integer> getSelectedRowIndexs() {
		return this.messageTablePanel.getSelectedRowIndexs();
	}

	public List<MessageDto> getSelectedMessages() {
		return this.messageTablePanel.getSelectedMessages();
	}

	public String getSelectedMessagesForCopyToClipboad() {
		return this.messageTablePanel.getSelectedMessagesForCopyToClipboad();
	}

	public void refreshMessageDetailPanels(MessageDto dto) {
		this.orgMessageEditorPanel.setMessage(dto);
		this.repeatMasterPanel.setup(dto);
		this.repeaterPanel.setup(dto);
		this.messageMemoPanel.enablePanel(dto);
	}

	public void refreshMessageRepeaterPanel(int rowIndex) {
		repeaterPanel.setMessage(rowIndex);
	}

	public void refreshComponentsDependOnAuthConfig() {
		repeaterPanel.refreshAuthAccountsComboBox();
	}

	public MessageRepeatDto sendRepeaterRequest(byte[] request, AuthAccountDto authAccountDto, MessageDto orgMessageDto) {
		if(authAccountDto.getId() != null && authAccountDto.getSessionId() == null) {
			fetchNewAuthSession(authAccountDto);
		}
		return RepeaterLogic.getInstance().sendRequest(request, authAccountDto, orgMessageDto, true);
	}

	public void sendRepeaterRequest() {
		repeaterPanel.sendRequest();
	}

	public MessageRepeatDto sendAutoRequest(List<PayloadDto> payloadDtos, MessageDto orgMessageDto) {
		return RepeaterLogic.getInstance().sendRequest(payloadDtos, orgMessageDto, false);
	}

	public MessageRepeatRedirectDto sendFollowRedirectRequest(byte[] request, byte[] response, MessageDto orgMessageDto) {
		return RepeaterLogic.getInstance().sendFollowRedirectRequest(request, response, orgMessageDto);
	}

	public void saveRepeatMaster(MessageDto messageDto) {
		MessageLogic.getInstance().saveRepeatMaster(messageDto);
		repeatMasterPanel.refreshPanel();
	}

	public void saveMessageMemo(MessageDto messageDto) {
		if(messageDto.getMemoIdWithoutLoad() == null) {
			MemoLogic.getInstance().saveMessageMemo(messageDto);
			return;
		}
		MemoLogic.getInstance().updateMessageMemo(messageDto);
	}

	public List<ProjectMemoDto> getProjectMemos() {
		var ret = MemoLogic.getInstance().loadProjectMemos();
		if(!ret.isEmpty()) {
			return ret;
		}

		var templates = ConfigLogic.getInstance().getUserOptions().getProjectMemoTemplates();
		if(templates == null) {
			return ret;
		}

		return templates.stream()
			.map(template -> {
				var dto = new ProjectMemoDto();
				dto.setMemo(template);
				MemoLogic.getInstance().saveProjectMemo(dto); // [CAUTION] insert template values
				return dto;
			})
			.collect(Collectors.toList());

	}
	public void saveProjectMemo(ProjectMemoDto dto) {
		MemoLogic.getInstance().saveProjectMemo(dto);
	}

	public void exportMemoToTxtFile(File file, boolean useFilter) {
		var messages = loadMessages();
		if(useFilter) {
			messages = MessageFilterLogic.getInstance().filter(messages, mainHeaderPanel.getMessageFilterDto());
		}

		ExportLogic.getInstance().exportMemoToTextFile(file, messages, getProjectMemos());
	}

	public List<AuthAccountDto> getAuthAccounts() {
		return AuthLogic.getInstance().loadAuthAccounts();
	}
	public void saveAuthAccount(AuthAccountDto authAccountDto) {
		AuthLogic.getInstance().saveAuthAccount(authAccountDto);
	}
	public void deleteAuthAccounts(List<AuthAccountDto> authAccountDtos) {
		AuthLogic.getInstance().deleteAuthAccounts(authAccountDtos);
	}

	public void fetchNewAuthSession(AuthAccountDto authAccountDto, MessageChainDto authMessageChainDto, boolean isTest) {
		AuthLogic.getInstance().sendLoginRequestAndSetSessionId(authAccountDto, authMessageChainDto, isTest);
	}
	public void fetchNewAuthSession(AuthAccountDto authAccountDto) {
		AuthLogic.getInstance().sendLoginRequestAndSetSessionId(authAccountDto);
	}

	public AuthConfigDto getAuthConfig() {
		var ret = AuthLogic.getInstance().loadAuthConfig();
		var messageChainDto = MessageChainLogic.getInstance().loadMessageChain(ret.getAuthMessageChainId());
		ret.setAuthMessageChainDto(messageChainDto);
		return ret;
	}

	public AuthConfigDto saveAuthConfig(MessageChainDto messageChainDto) {
		MessageChainLogic.getInstance().saveMessageChain(messageChainDto);

		var authConfigDto = ConfigLogic.getInstance().getAuthConfig();
		if(authConfigDto == null) {
			authConfigDto = new AuthConfigDto();
		}
		authConfigDto.setAuthMessageChainDto(messageChainDto);
		AuthLogic.getInstance().saveAuthConfig(authConfigDto);

		AuthLogic.getInstance().clearAuthAccountsSession();

		refreshComponentsDependOnAuthConfig();

		return authConfigDto;
	}

	public PluginInfo loadPlugin(String pluginJarFilePath) {
		return PluginManager.getInstance().load(pluginJarFilePath);
	}
	public void unloadPlugin(PluginInfo pluginInfo) {
		PluginManager.getInstance().unload(pluginInfo);
	}

	public void addPluginContextMenuFactories(List<IContextMenuFactory> pluginContextMenuFactories) {
		((MessageTablePopupMenu)messageTable.getComponentPopupMenu()).addPluginContextMenuFactories(pluginContextMenuFactories);
	}
	public void removePluginContextMenuFactories(List<IContextMenuFactory> pluginContextMenuFactories) {
		((MessageTablePopupMenu)messageTable.getComponentPopupMenu()).removePluginContextMenuFactories(pluginContextMenuFactories);
	}

	public void addPluginTabs(List<ITab> pluginTabs) {
		pluginsPanel.addPluginTabs(pluginTabs);
	}
	public void removePluginTabs(List<ITab> pluginTabs) {
		pluginsPanel.removePluginTabs(pluginTabs);
	}


	public void loadDatabase() {
		List<MessageDto> messageDtos = loadMessages();
		this.messageTableModel.addRows(messageDtos);
		applyMessageFilter();
		this.projectMemoPanel.refreshPanel();
		this.authPanel.refreshPanel(messageDtos);
		refreshComponentsDependOnAuthConfig();
	}

	private List<MessageDto> loadMessages() {
		List<Integer> messageOrder = MessageLogic.getInstance().loadMessageOrder();
		List<MessageDto> messageDtos = MessageLogic.getInstance().loadMessages();

		return messageOrder.stream()
			.map(messageId -> messageDtos.stream().filter(dto -> dto.getId().equals(messageId)).findFirst().get())
			.collect(Collectors.toList());
	}

	public void applyMessageFilter() {
		mainHeaderPanel.applyMessageFilter();
	}
	/**
	 * @return number of rows after filtering
	 */
	public int applyMessageFilter(MessageFilterDto messageFilterDto) {
		return messageTablePanel.applyFilter(messageFilterDto);
	}

	public void changeDatabase(String dbFilePath) {
		ConfigLogic.getInstance().saveDbFilePath(dbFilePath);
		DatabaseManager.getInstance().changeDatabase(dbFilePath);
		ProjectLogic.getInstance().selectProject();
		mainHeaderPanel.refreshProjectName();
		ConfigLogic.getInstance().resetProjectOptionsDto();
		reloadDatabase();
	}
	private void reloadDatabase() {
		this.messageTableModel.clearRows();
		this.messageTablePanel.setupTable(); // for fix bug: progress column control can't be active after to load empty messageTable.
		this.orgMessageEditorPanel.clearMessage();
		this.repeatMasterPanel.clear();
		this.repeaterPanel.clear();
		this.messageMemoPanel.disablePanel();

		loadDatabase();
	}


	public void loadPlugins() {
		pluginsPanel.loadUserOption();
	}

}
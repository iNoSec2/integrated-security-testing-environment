package okuken.iste.view.header;

import javax.swing.JPanel;

import com.google.common.collect.Lists;

import okuken.iste.consts.Captions;
import okuken.iste.controller.Controller;
import okuken.iste.dto.MessageFilterDto;
import okuken.iste.enums.SecurityTestingProgress;
import okuken.iste.logic.ConfigLogic;

import java.awt.FlowLayout;

import javax.swing.JButton;

import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class MainHeaderPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private JLabel projectNameLabel;
	private List<JCheckBox> progressCheckboxs;
	private JLabel rowCountLabel;

	private JButton dockoutButton;
	private JTextField searchTextField;

	public MainHeaderPanel() {
		setLayout(new BorderLayout(0, 0));
		
		JPanel leftPanel = new JPanel();
		add(leftPanel, BorderLayout.WEST);
		
		progressCheckboxs = Lists.newArrayList();
		Arrays.stream(SecurityTestingProgress.values()).forEach(progress -> {
			var progressCheckbox = new JCheckBox(progress.getCaption());
			progressCheckbox.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					applyMessageFilter();
				}
			});
			progressCheckbox.setSelected(true);
			leftPanel.add(progressCheckbox);
			progressCheckboxs.add(progressCheckbox);
		});
		
		searchTextField = new JTextField();
		leftPanel.add(searchTextField);
		searchTextField.setColumns(20);
		searchTextField.getDocument().addDocumentListener(new DocumentListener() {
			public void removeUpdate(DocumentEvent e) {apply();}
			public void insertUpdate(DocumentEvent e) {apply();}
			public void changedUpdate(DocumentEvent e) {apply();}
			private void apply() {
				applyMessageFilter();
			}
		});
		
		rowCountLabel = new JLabel("");
		leftPanel.add(rowCountLabel);
		
		JPanel centerPanel = new JPanel();
		projectNameLabel = new JLabel();
		refreshProjectName();
		centerPanel.add(projectNameLabel);
		add(centerPanel, BorderLayout.CENTER);
		
		JPanel rightPanel = new JPanel();
		FlowLayout flowLayout = (FlowLayout) rightPanel.getLayout();
		flowLayout.setAlignment(FlowLayout.RIGHT);
		
		JButton initColumnWidthButton = new JButton(Captions.MAIN_HEADER_BUTTON_INIT_COLUMN_WIDTH);
		initColumnWidthButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Controller.getInstance().initSizeRatioOfParts();
			}
		});
		rightPanel.add(initColumnWidthButton);
		
		dockoutButton = new JButton();
		rightPanel.add(dockoutButton);
		
		add(rightPanel, BorderLayout.EAST);

		Controller.getInstance().setMainHeaderPanel(this);
	}

	public void applyMessageFilter() {
		var dto = new MessageFilterDto();
		dto.setProgresses(progressCheckboxs.stream()
			.filter(progressCheckbox -> progressCheckbox.isSelected())
			.map(progressCheckbox -> SecurityTestingProgress.getByCaption(progressCheckbox.getText()))
			.collect(Collectors.toList()));

		dto.setSearchWord(searchTextField.getText());

		int rowCount = Controller.getInstance().applyMessageFilter(dto);
		setRowCount(rowCount);
	}

	private void setRowCount(int rowCount) {
		rowCountLabel.setText(String.format(" [%d] ", rowCount));
	}

	public void refreshProjectName() {
		projectNameLabel.setText(ConfigLogic.getInstance().getProcessOptions().getProjectDto().getName());
	}

	public JButton getDockoutButton() {
		return dockoutButton;
	}

}

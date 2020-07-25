package okuken.iste.view;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import okuken.iste.consts.Captions;
import okuken.iste.controller.Controller;
import okuken.iste.util.UiUtil;

public abstract class AbstractDockoutableTabPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private JFrame dockoutFrame;

	protected abstract AbstractButton getDockoutButton();
	protected abstract String getTabName();
	protected abstract int getTabIndex();

	protected JTabbedPane getParentTabbedPane() {
		return Controller.getInstance().getMainTabbedPane();
	}

	/**
	 * [CAUTION] need to call after create dockoutButton
	 */
	protected void setupDockout() {
		var dockoutButton = getDockoutButton();
		dockoutButton.setText(Captions.DOCKOUT);
		dockoutButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dockoutOrDockin();
			}
		});
	}

	private void dockoutOrDockin() {
		if(dockoutFrame == null) {
			dockoutFrame = UiUtil.dockout(UiUtil.createDockoutTitleByTabName(getTabName()), this);
			getDockoutButton().setText(Captions.DOCKIN);
		} else {
			UiUtil.dockin(getTabName(), this, getTabIndex(), getParentTabbedPane(), dockoutFrame);
			dockoutFrame = null;
			getDockoutButton().setText(Captions.DOCKOUT);
		}
	}

}

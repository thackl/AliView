package aliview.messenges;

import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import aliview.AliViewWindow;

public class TextEditDialog {
	private static final String LF = System.getProperty("line.separator");
	public static final Message EDIT_SEQUENCE_NAME = new Message("", "Rename sequence");
	private static JTextField textEdit;
	protected Integer selectedValue = -1;

	
	public void showOKCancelTextEditor(String editString, Message message, AliViewWindow aliViewWindow) {
		
		final JDialog dialog = new JDialog(aliViewWindow);
		dialog.setTitle(message.title);
		dialog.setModal(true);
		dialog.setAlwaysOnTop(true);
		dialog.setModalityType(ModalityType.DOCUMENT_MODAL);
		
		textEdit = new JTextField(editString);
		textEdit.setEditable(true);
		
		JPanel testPanel = new JPanel();
		testPanel.setPreferredSize(new Dimension(350,10));
		testPanel.add(textEdit);
		
		Object[] messageAndEditor = new Object[]{testPanel,textEdit};
		
		JOptionPane optPane = new JOptionPane(messageAndEditor,JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		optPane.addPropertyChangeListener(new PropertyChangeListener()
		   {
		      public void propertyChange(PropertyChangeEvent e)
		      {
		         if (e.getPropertyName().equals("value"))
		         {
		        	 
		        	selectedValue = (Integer) e.getNewValue();
		            switch (selectedValue)
		            {
		               case JOptionPane.OK_OPTION:
		                  break;
		               case JOptionPane.CANCEL_OPTION:
		                  //user clicks CANCEL
		                  break;                       
		            }
		            dialog.dispose();
		         }
		      }
		   });
		
		dialog.setContentPane(optPane);
		dialog.pack();
		dialog.setLocationRelativeTo(aliViewWindow);
		dialog.setVisible(true);
	}
	
	public Integer getSelectedValue() {
		return selectedValue;
	}

	public String getEditText() {
		return textEdit.getText();
	}
}

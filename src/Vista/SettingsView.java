package Vista;

import Controlador.Controller;

import javax.swing.*;
import java.awt.*;

public class SettingsView extends View {
    private JButton jbChangePassword;
    private JButton jbBack;
    private JButton jbAddMoney;
    private JButton jbWalletEvolution;

    public SettingsView(){
        this.setLayout(new BorderLayout());

        //Panell que té el títol de la pantalla a dalt a la dreta al mig
        JPanel jpTitle = new JPanel();
        JPanel jpgblTitle = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        JLabel jlTitle = new JLabel("SETTINGS");
        jlTitle.setFont(new Font("ArialBlack", Font.BOLD, 24));
        //Marges
        c.insets = new Insets(20,0,0,0);
        jpgblTitle.add(jlTitle, c);
        jpTitle.add(jpgblTitle);
        this.add(jpTitle, BorderLayout.NORTH);

        jbBack = new JButton("BACK");
        jbBack.setFocusable(false);
        jbBack.setPreferredSize(new Dimension(100,30));

        //Panell per col·locar el botó back a la part baixa a l'esquerra
        JPanel jpgblExit = new JPanel(new GridBagLayout());
        //Marges
        c.insets = new Insets(0,20,20,0);
        c.fill = GridBagConstraints.BOTH;
        jpgblExit.add(jbBack, c);
        JPanel jpExit = new JPanel(new FlowLayout(FlowLayout.LEFT));
        jpExit.add(jpgblExit);
        this.add(jpExit, BorderLayout.SOUTH);

        //Panell que té els botons per accedir a les diferents opcions
        JPanel jpgblBotons = new JPanel(new GridBagLayout());

        c.insets = new Insets(0,0,20,0);
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;

        jbChangePassword = new JButton("CHANGE PASSWORD");
        jbChangePassword.setFocusable(false);
        jbAddMoney = new JButton("ADD MONEY");
        jbAddMoney.setFocusable(false);
        jbWalletEvolution = new JButton("WALLET EVOLUTION");
        jbWalletEvolution.setFocusable(false);

        jpgblBotons.add(jbChangePassword, c);

        c.gridy = 1;
        jpgblBotons.add(jbAddMoney, c);

        c.gridy = 2;
        c.insets = new Insets(0,0,0,0);
        jpgblBotons.add(jbWalletEvolution, c);

        this.add(jpgblBotons, BorderLayout.CENTER);
    }

    @Override
    public void addController(Controller c) {
        jbChangePassword.setActionCommand("changePass");
        jbChangePassword.addActionListener(c);

        jbBack.setActionCommand("backFromSettings");
        jbBack.addActionListener(c);

        jbAddMoney.setActionCommand("addMoneyButton");
        jbAddMoney.addActionListener(c);

        jbWalletEvolution.setActionCommand("walletEvolution");
        jbWalletEvolution.addActionListener(c);
    }
}
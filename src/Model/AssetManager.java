package Model;

import Controlador.Sounds;
import Vista.SplashScreen.SplashScreen;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AssetManager {

    private static Map<String, BufferedImage> imatges;
    private final static String PATH_IMATGES = "./Assets/Images";

    public static void loadData(SplashScreen splashScreen) {
        Baralla.loadContent(splashScreen);
        loadImatges(splashScreen);
        Sounds.loadAllSounds(splashScreen);
    }

    private static void loadImatges(SplashScreen splashScreen){

        File carpetaAssetsCartes = new File(PATH_IMATGES);
        File[] listOfFiles = carpetaAssetsCartes.listFiles();

        imatges = new HashMap<>();

        if (listOfFiles != null) {
            for (File foto : listOfFiles) {
                BufferedImage img = null;
                try {
                    img = ImageIO.read(foto);
                } catch (IOException e) {
                    System.out.println("Error llegint " + foto.getName());
                    splashScreen.stop();
                }
                if (img != null) {
                    imatges.put(foto.getName(), img);
                    splashScreen.infoMessage("Loaded " + imatges.size() + " UI images.");
                }
            }
        }
    }
    public static Image getImage(String nom){
        return imatges.get(nom);
    }
}
package ua.dp.budash.mika.firebaseinnovecs;

/**
 * Created by Mika on 01.07.2017.
 */

public class Utils {

    static String filter(String original, String[] ignores) {
        for (String i : ignores) {
            original = original.replaceAll("(?i)\\b" + i + "\\b", "*****");
        }
        return original;
    }
}

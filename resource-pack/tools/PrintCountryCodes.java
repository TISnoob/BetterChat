import java.util.Locale;

public final class PrintCountryCodes {
    public static void main(String[] args) {
        System.out.print(String.join("\n", Locale.getISOCountries()));
    }
}

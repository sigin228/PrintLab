package kz.printlab.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

public final class KazakhstanPhone {
    private KazakhstanPhone() { }
    public static String normalize(String value) {
        if (value == null || !value.matches("[+0-9 ()-]{1,30}")) throw invalid();
        try {
            var util = PhoneNumberUtil.getInstance();
            var number = util.parse(value, "KZ");
            if (!util.isValidNumberForRegion(number, "KZ")) throw invalid();
            return util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) { throw invalid(); }
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Укажите казахстанский номер, например +7 777 123 45 67.");
    }
}

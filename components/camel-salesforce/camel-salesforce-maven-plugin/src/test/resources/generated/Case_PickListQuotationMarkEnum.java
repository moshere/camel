/*
 * Salesforce DTO generated by camel-salesforce-maven-plugin
 * Generated on: Thu Mar 09 16:15:49 ART 2017
 */
package $packageName;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Salesforce Enumeration DTO for picklist PickListQuotationMark
 */
public enum Case_PickListQuotationMarkEnum {

    // No apretar "miralo"
    NO_APRETAR__MIRALO_("No apretar \"miralo\"");

    final String value;

    private Case_PickListQuotationMarkEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    @JsonCreator
    public static Case_PickListQuotationMarkEnum fromValue(String value) {
        for (Case_PickListQuotationMarkEnum e : Case_PickListQuotationMarkEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException(value);
    }

}

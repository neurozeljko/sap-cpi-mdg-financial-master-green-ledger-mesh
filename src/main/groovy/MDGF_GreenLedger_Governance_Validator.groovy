import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

def Message processData(Message message) {
    def reader = message.getBody(java.io.Reader)
    def payload = new JsonSlurper().parse(reader)

    String companyCode = (payload.CompanyCode ?: "").trim().toUpperCase()
    String chartOfAccounts = (payload.ChartOfAccounts ?: "YCOA").trim().toUpperCase()
    String glAccount = (payload.GLAccount ?: "").trim()
    String accountName = (payload.GLAccountName ?: "").trim()
    String accountType = (payload.GLAccountType ?: "").trim().toUpperCase()
    String currency = (payload.Currency ?: "").trim().toUpperCase()
    String costCenter = (payload.CostCenter ?: "").trim().toUpperCase()
    String profitCenter = (payload.ProfitCenter ?: "").trim().toUpperCase()
    String validFromStr = (payload.ValidFrom ?: "2026-01-01").trim()
    String validToStr = (payload.ValidTo ?: "9999-12-31").trim()

    boolean isGreenLedger = (payload.IsGreenLedgerCarbonAccount == true)
    String ghgScope = (payload.GhgScopeCategory ?: "").trim().toUpperCase()
    String csrdStandard = (payload.CsrdEsrsStandard ?: "").trim().toUpperCase()
    String emissionUnit = (payload.EmissionUnit ?: "").trim().toUpperCase()

    List<String> governanceViolations = []
    if (!companyCode.matches("^[A-Z0-9]{4}\$")) governanceViolations.add("INVALID_COMPANY_CODE: CompanyCode [${companyCode}] must be 4 alphanumeric characters.")
    Set<String> validIsoCurrencies = ["EUR", "USD", "CHF", "GBP", "RSD", "JPY", "SEK", "PLN", "CZK", "HUF"] as Set
    if (!validIsoCurrencies.contains(currency)) governanceViolations.add("INVALID_ISO4217_CURRENCY: Currency [${currency}] is not permitted in Ledger 0L.")

    if (!glAccount.matches("^\\d{6,10}\$")) governanceViolations.add("INVALID_GL_ACCOUNT_FORMAT: GLAccount [${glAccount}] must be 6 to 10 numeric digits.")
    Set<String> allowedAccountTypes = ["X", "N", "P", "S", "C"] as Set
    if (!allowedAccountTypes.contains(accountType)) {
        governanceViolations.add("INVALID_GL_ACCOUNT_TYPE: GLAccountType [${accountType}] must be one of [X, N, P, S, C].")
    } else if (glAccount.length() >= 6) {
        char leadingDigit = glAccount.charAt(0)
        if ((accountType == "X" || accountType == "C") && !(leadingDigit in ['1', '2', '3'] as char[])) {
            governanceViolations.add("COA_RANGE_VIOLATION: Balance Sheet/Cash account [${glAccount}] must start with digits 1-3.")
        }
        if ((accountType == "P" || accountType == "S" || accountType == "N") && !(leadingDigit in ['4', '5', '6', '7', '8', '9'] as char[])) {
            governanceViolations.add("COA_RANGE_VIOLATION: P&L/Controlling account [${glAccount}] must start with digits 4-9.")
        }
    }

    try {
        if (LocalDate.parse(validFromStr).isAfter(LocalDate.parse(validToStr))) {
            governanceViolations.add("INVALID_VALIDITY_INTERVAL: ValidFrom cannot be after ValidTo.")
        }
    } catch (Exception ignored) {
        governanceViolations.add("MALFORMED_ISO_DATE: ValidFrom and ValidTo must follow YYYY-MM-DD format.")
    }

    if (isGreenLedger) {
        Set<String> allowedScopes = ["SCOPE_1", "SCOPE_2_LOCATION", "SCOPE_2_MARKET", "SCOPE_3_UPSTREAM", "SCOPE_3_DOWNSTREAM"] as Set
        Set<String> allowedEsrs = ["ESRS_E1_6", "ESRS_E1_7"] as Set
        Set<String> allowedUnits = ["TCO2E", "KGCO2E"] as Set
        if (!allowedScopes.contains(ghgScope)) governanceViolations.add("GREEN_LEDGER_INVALID_SCOPE: GhgScopeCategory [${ghgScope}] is invalid.")
        if (!allowedEsrs.contains(csrdStandard)) governanceViolations.add("CSRD_TAXONOMY_MISMATCH: CsrdEsrsStandard [${csrdStandard}] must align with ESRS_E1_6 or ESRS_E1_7.")
        if (!allowedUnits.contains(emissionUnit)) governanceViolations.add("INVALID_CARBON_UOM: EmissionUnit [${emissionUnit}] must be TCO2E or KGCO2E.")
        if (!(accountType in ["P", "S"])) governanceViolations.add("GREEN_LEDGER_ACCOUNT_TYPE_ERROR: Carbon accounts must use GLAccountType P or S.")
    }

    String mdgfGoldenHash = MessageDigest.getInstance("SHA-256")
        .digest("${chartOfAccounts}|${companyCode}|${glAccount}|${accountType}|${costCenter}|${profitCenter}|${ghgScope}".getBytes("UTF-8"))
        .encodeHex().toString()
    message.setHeader("X-MDGF-Golden-Hash", mdgfGoldenHash)

    if (!governanceViolations.isEmpty()) {
        message.setHeader("X-MDGF-Gate-Status", "REJECTED")
        message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
            status: "QUARANTINED_MDGF_GOVERNANCE_HOLD", framework: "SAP_S4HANA_2025_MDGF_AND_GREEN_LEDGER_CSRD",
            timestamp: Instant.now().toString(), companyCode: companyCode, chartOfAccounts: chartOfAccounts,
            glAccount: glAccount, mdgfGoldenSha256: mdgfGoldenHash, violations: governanceViolations
        ])))
    } else {
        message.setHeader("X-MDGF-Gate-Status", "APPROVED")
        message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
            "@odata.context": "\$metadata#A_GLAccountInChartOfAccounts/\$entity", ChartOfAccounts: chartOfAccounts,
            GLAccount: glAccount, GLAccountType: accountType, CompanyCode: companyCode, Currency: currency,
            ValidityStartDate: validFromStr, ValidityEndDate: validToStr, MDGFGoldenFingerprint: mdgfGoldenHash,
            to_Text: [[Language: "EN", GLAccountName: accountName, GLAccountLongName: accountName]],
            to_ControllingAssignment: [CostCenter: costCenter, ProfitCenter: profitCenter],
            SAPGreenLedgerExtension: isGreenLedger ? [CarbonAccountingEnabled: true, GhgProtocolScope: ghgScope, CsrdDisclosureTag: csrdStandard, CarbonUnitOfMeasure: emissionUnit, LedgerTarget: "0L_GREEN_UNIVERSAL_JOURNAL"] : [CarbonAccountingEnabled: false]
        ])))
    }
    return message
}

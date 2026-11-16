import spock.lang.Specification
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.sap.gateway.ip.core.customdev.util.Message

class MdgfGreenLedgerValidatorSpec extends Specification {
    def script = new GroovyShell().parse(new File("src/main/groovy/MDGF_GreenLedger_Governance_Validator.groovy"))

    def "Should approve valid S/4HANA 2025 Green Ledger G/L account"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            CompanyCode: "DE01", ChartOfAccounts: "YCOA", GLAccount: "61004500",
            GLAccountName: "Scope 1 Carbon Allocation", GLAccountType: "P", Currency: "EUR",
            CostCenter: "CC-MFG-01", ProfitCenter: "PC-EMEA-EV", IsGreenLedgerCarbonAccount: true,
            GhgScopeCategory: "SCOPE_1", CsrdEsrsStandard: "ESRS_E1_6", EmissionUnit: "TCO2E"
        ]))
        when:
        def res = script.processData(msg)
        def json = new JsonSlurper().parseText(res.getBody(String))
        then:
        res.getHeaders()["X-MDGF-Gate-Status"] == "APPROVED"
        json.SAPGreenLedgerExtension.CarbonAccountingEnabled == true
    }

    def "Should reject G/L account violating YCOA leading digit range"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            CompanyCode: "DE01", ChartOfAccounts: "YCOA", GLAccount: "61001000",
            GLAccountName: "Invalid Cash Account", GLAccountType: "X", Currency: "EUR"
        ]))
        when:
        def res = script.processData(msg)
        then:
        res.getHeaders()["X-MDGF-Gate-Status"] == "REJECTED"
    }

    def "Should quarantine Green Ledger carbon account failing EU CSRD ESRS E1 taxonomy"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            CompanyCode: "DE01", ChartOfAccounts: "YCOA", GLAccount: "11002000",
            GLAccountName: "Bad Carbon Account", GLAccountType: "X", Currency: "EUR",
            IsGreenLedgerCarbonAccount: true, GhgScopeCategory: "INVALID", CsrdEsrsStandard: "ESRS_S1", EmissionUnit: "KG"
        ]))
        when:
        def res = script.processData(msg)
        then:
        res.getHeaders()["X-MDGF-Gate-Status"] == "REJECTED"
    }
}

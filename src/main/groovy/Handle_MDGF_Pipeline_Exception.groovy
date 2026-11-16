import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant

def Message processData(Message message) {
    def ex = message.getProperties().get("CamelExceptionCaught")
    message.setHeader("CamelHttpResponseCode", 500)
    message.setHeader("Content-Type", "application/json")
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
        error: "MDGF_GREEN_LEDGER_PIPELINE_ERROR", timestamp: Instant.now().toString(),
        details: ex ? ex.getMessage() : "Unhandled MDG-F Financial Master Execution Failure"
    ])))
    return message
}

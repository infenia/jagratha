package gg.jte.generated.ondemand;
import com.infenia.jagratha.model.WorkflowProgress;
import com.infenia.jagratha.model.TaskProgress;
import com.infenia.jagratha.model.WorkflowDefinition;
import java.util.Map;
import java.util.List;
@SuppressWarnings("unchecked")
public final class JtesessionGenerated {
	public static final String JTE_NAME = "session.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,12,12,12,12,18,18,18,21,21,22,22,22,22,24,24,25,25,26,26,26,26,26,26,26,26,26,26,26,26,27,27,28,28,28,28,28,28,28,28,28,28,28,28,29,29,30,30,32,32,34,34,35,35,35,36,36,38,38,44,49,49,50,50,52,52,52,53,53,53,55,55,56,56,61,102,102,103,103,103,103,103,103,104,104,107,107,108,108,108,108,108,108,109,109,124,136,170,170,170,183,183,183,184,184,184,5,6,7,8,9,10,10,10,10};
	private static final gg.jte.runtime.BinaryContent BINARY_CONTENT = gg.jte.runtime.BinaryContent.load(JtesessionGenerated.class, "JtesessionGenerated.bin", 1,694,79,70,276,25,36,8,1,10,34,36,8,1,1,34,21,39,62,232,24,231,113,364,25,169,115,66,21,71,2314,28,11,21,64,32,12,21,472,473,1503,716,1);
	private static final byte[] TEXT_PART_BINARY_0 = BINARY_CONTENT.get(0);
	private static final byte[] TEXT_PART_BINARY_1 = BINARY_CONTENT.get(1);
	private static final byte[] TEXT_PART_BINARY_2 = BINARY_CONTENT.get(2);
	private static final byte[] TEXT_PART_BINARY_3 = BINARY_CONTENT.get(3);
	private static final byte[] TEXT_PART_BINARY_4 = BINARY_CONTENT.get(4);
	private static final byte[] TEXT_PART_BINARY_5 = BINARY_CONTENT.get(5);
	private static final byte[] TEXT_PART_BINARY_6 = BINARY_CONTENT.get(6);
	private static final byte[] TEXT_PART_BINARY_7 = BINARY_CONTENT.get(7);
	private static final byte[] TEXT_PART_BINARY_8 = BINARY_CONTENT.get(8);
	private static final byte[] TEXT_PART_BINARY_9 = BINARY_CONTENT.get(9);
	private static final byte[] TEXT_PART_BINARY_10 = BINARY_CONTENT.get(10);
	private static final byte[] TEXT_PART_BINARY_11 = BINARY_CONTENT.get(11);
	private static final byte[] TEXT_PART_BINARY_12 = BINARY_CONTENT.get(12);
	private static final byte[] TEXT_PART_BINARY_13 = BINARY_CONTENT.get(13);
	private static final byte[] TEXT_PART_BINARY_14 = BINARY_CONTENT.get(14);
	private static final byte[] TEXT_PART_BINARY_15 = BINARY_CONTENT.get(15);
	private static final byte[] TEXT_PART_BINARY_16 = BINARY_CONTENT.get(16);
	private static final byte[] TEXT_PART_BINARY_17 = BINARY_CONTENT.get(17);
	private static final byte[] TEXT_PART_BINARY_18 = BINARY_CONTENT.get(18);
	private static final byte[] TEXT_PART_BINARY_19 = BINARY_CONTENT.get(19);
	private static final byte[] TEXT_PART_BINARY_20 = BINARY_CONTENT.get(20);
	private static final byte[] TEXT_PART_BINARY_21 = BINARY_CONTENT.get(21);
	private static final byte[] TEXT_PART_BINARY_22 = BINARY_CONTENT.get(22);
	private static final byte[] TEXT_PART_BINARY_23 = BINARY_CONTENT.get(23);
	private static final byte[] TEXT_PART_BINARY_24 = BINARY_CONTENT.get(24);
	private static final byte[] TEXT_PART_BINARY_25 = BINARY_CONTENT.get(25);
	private static final byte[] TEXT_PART_BINARY_26 = BINARY_CONTENT.get(26);
	private static final byte[] TEXT_PART_BINARY_27 = BINARY_CONTENT.get(27);
	private static final byte[] TEXT_PART_BINARY_28 = BINARY_CONTENT.get(28);
	private static final byte[] TEXT_PART_BINARY_29 = BINARY_CONTENT.get(29);
	private static final byte[] TEXT_PART_BINARY_30 = BINARY_CONTENT.get(30);
	private static final byte[] TEXT_PART_BINARY_31 = BINARY_CONTENT.get(31);
	private static final byte[] TEXT_PART_BINARY_32 = BINARY_CONTENT.get(32);
	private static final byte[] TEXT_PART_BINARY_33 = BINARY_CONTENT.get(33);
	private static final byte[] TEXT_PART_BINARY_34 = BINARY_CONTENT.get(34);
	private static final byte[] TEXT_PART_BINARY_35 = BINARY_CONTENT.get(35);
	private static final byte[] TEXT_PART_BINARY_36 = BINARY_CONTENT.get(36);
	private static final byte[] TEXT_PART_BINARY_37 = BINARY_CONTENT.get(37);
	private static final byte[] TEXT_PART_BINARY_38 = BINARY_CONTENT.get(38);
	private static final byte[] TEXT_PART_BINARY_39 = BINARY_CONTENT.get(39);
	private static final byte[] TEXT_PART_BINARY_40 = BINARY_CONTENT.get(40);
	private static final byte[] TEXT_PART_BINARY_41 = BINARY_CONTENT.get(41);
	private static final byte[] TEXT_PART_BINARY_42 = BINARY_CONTENT.get(42);
	public static void render(gg.jte.html.HtmlTemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, String sessionId, String actualWorkflowId, Map<String, Object> config, WorkflowDefinition workflow, List<String> logs, WorkflowProgress progress) {
		jteOutput.writeBinaryContent(TEXT_PART_BINARY_0);
		gg.jte.generated.ondemand.layout.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, "Session " + sessionId, new gg.jte.html.HtmlContent() {
			public void writeTo(gg.jte.html.HtmlTemplateOutput jteOutput) {
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_1);
				jteOutput.setContext("h1", null);
				jteOutput.writeUserContent(sessionId);
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_2);
				if (config.get("workflows") instanceof Map) {
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_3);
					jteOutput.setContext("select", "onchange");
					jteOutput.writeUserContent(sessionId);
					jteOutput.setContext("select", null);
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_4);
					for (String wId : ((Map<String, Object>)config.get("workflows")).keySet()) {
						jteOutput.writeBinaryContent(TEXT_PART_BINARY_5);
						if (wId.equals(actualWorkflowId)) {
							jteOutput.writeBinaryContent(TEXT_PART_BINARY_6);
							var __jte_html_attribute_0 = wId;
							if (gg.jte.runtime.TemplateUtils.isAttributeRendered(__jte_html_attribute_0)) {
								jteOutput.writeBinaryContent(TEXT_PART_BINARY_7);
								jteOutput.setContext("option", "value");
								jteOutput.writeUserContent(__jte_html_attribute_0);
								jteOutput.setContext("option", null);
								jteOutput.writeBinaryContent(TEXT_PART_BINARY_8);
							}
							jteOutput.writeBinaryContent(TEXT_PART_BINARY_9);
							jteOutput.setContext("option", null);
							jteOutput.writeUserContent(wId);
							jteOutput.writeBinaryContent(TEXT_PART_BINARY_10);
						} else {
							jteOutput.writeBinaryContent(TEXT_PART_BINARY_11);
							var __jte_html_attribute_1 = wId;
							if (gg.jte.runtime.TemplateUtils.isAttributeRendered(__jte_html_attribute_1)) {
								jteOutput.writeBinaryContent(TEXT_PART_BINARY_12);
								jteOutput.setContext("option", "value");
								jteOutput.writeUserContent(__jte_html_attribute_1);
								jteOutput.setContext("option", null);
								jteOutput.writeBinaryContent(TEXT_PART_BINARY_13);
							}
							jteOutput.writeBinaryContent(TEXT_PART_BINARY_14);
							jteOutput.setContext("option", null);
							jteOutput.writeUserContent(wId);
							jteOutput.writeBinaryContent(TEXT_PART_BINARY_15);
						}
						jteOutput.writeBinaryContent(TEXT_PART_BINARY_16);
					}
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_17);
				}
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_18);
				if (progress != null) {
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_19);
					jteOutput.setContext("span", null);
					jteOutput.writeUserContent(progress.status());
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_20);
				} else {
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_21);
				}
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_22);
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_23);
				for (Map.Entry<String, Object> entry : config.entrySet()) {
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_24);
					if (!entry.getKey().equals("workflows")) {
						jteOutput.writeBinaryContent(TEXT_PART_BINARY_25);
						jteOutput.setContext("div", null);
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeBinaryContent(TEXT_PART_BINARY_26);
						jteOutput.setContext("div", null);
						jteOutput.writeUserContent(String.valueOf(entry.getValue()));
						jteOutput.writeBinaryContent(TEXT_PART_BINARY_27);
					}
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_28);
				}
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_29);
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_30);
				for (WorkflowDefinition.Node node : workflow.nodes()) {
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_31);
					jteOutput.setContext("script", null);
					jteOutput.writeUserContent(node.nodeId());
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_32);
					jteOutput.setContext("script", null);
					jteOutput.writeUserContent(node.nodeId());
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_33);
				}
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_34);
				for (WorkflowDefinition.Edge edge : workflow.edges()) {
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_35);
					jteOutput.setContext("script", null);
					jteOutput.writeUserContent(edge.source());
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_36);
					jteOutput.setContext("script", null);
					jteOutput.writeUserContent(edge.target());
					jteOutput.writeBinaryContent(TEXT_PART_BINARY_37);
				}
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_38);
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_39);
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_40);
				jteOutput.setContext("script", null);
				jteOutput.writeUserContent(sessionId);
				jteOutput.writeBinaryContent(TEXT_PART_BINARY_41);
			}
		});
		jteOutput.writeBinaryContent(TEXT_PART_BINARY_42);
	}
	public static void renderMap(gg.jte.html.HtmlTemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		String sessionId = (String)params.get("sessionId");
		String actualWorkflowId = (String)params.get("actualWorkflowId");
		Map<String, Object> config = (Map<String, Object>)params.get("config");
		WorkflowDefinition workflow = (WorkflowDefinition)params.get("workflow");
		List<String> logs = (List<String>)params.get("logs");
		WorkflowProgress progress = (WorkflowProgress)params.get("progress");
		render(jteOutput, jteHtmlInterceptor, sessionId, actualWorkflowId, config, workflow, logs, progress);
	}
}

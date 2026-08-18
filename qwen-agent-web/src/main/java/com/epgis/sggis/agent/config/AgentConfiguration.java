package com.epgis.sggis.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    private static final String SYSTEM_PROMPT = """
            你是一个友好、严谨的中文电网 GIS 智能助手。
            回答时优先使用系统提供的知识库上下文；知识库没有相关内容时，不要编造来源。

            当用户询问“某设备的上级电源”“电源来自哪里”或要求“电源追溯”时，必须按下面顺序调用 MCP 工具：
            1. 调用 searchPowerEquipment 搜索设备。
            2. 如果没有候选，提示用户补充信息；如果有多个候选，列出候选并等待用户确认，不能继续追溯。
            3. 设备唯一或用户已确认后，调用 tracePowerSource，默认 runStatus 使用字符串 1（实际开关状态）。
            4. 使用上一步返回的 traceId 调用 queryPowerEquipmentDetails。
            5. 最后按“电源点 → 上游设备 → 目标设备”的顺序输出完整供电路径，并明确上级电源名称。
            不得跳过工具、不得自行猜测 typeId、oid、searchId 或 traceId。

            当用户查询“某线路下的所有配电变压器”时，调用 queryLineDistributionTransformers：
            - 优先直接传入用户给出的线路名称或OID。
            - status 为 NEED_CONFIRMATION 时列出候选线路并等待用户确认，然后使用确定的线路OID再次调用。
            - status 为 COMPLETED 时，完整列出变压器名称、OID、电压等级和容量。

            当用户查询“某地址周边X公里内的变电站或变压器”时，必须按下面顺序调用：
            1. 调用 geocodeAddress，把用户地址和城市解析为坐标。
            2. 使用返回的 geocodeId 调用 queryNearbyPowerEquipment，不得自行猜测或修改坐标。
            3. 用户只问变电站时 category=STATION；只问变压器时 category=TRANSFORMER；两者都问时 category=ALL。
            4. 将用户给出的公里数作为 radiusKm；没有给出距离时先询问，不要自行假设。
            5. 最终按距离从近到远列出名称、类型、OID、电压等级和距离。

            当用户要求数学计算时，必须使用 calculate 工具，不要自行估算。
            对普通问题直接、准确地用中文回答。
            """;

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, VectorStore vectorStore) {
        var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(6)
                        .similarityThreshold(0.65)
                        .build())
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        ragAdvisor,
                        SimpleLoggerAdvisor.builder().build())
                .build();
    }
}

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
            
            当用户要求数学计算时，必须使用 calculate 工具，不要自行估算。

            当用户要求定位线路、变电站等设备时，调用 locateEquipment；返回 NEED_CONFIRMATION 时先确认候选，
            返回 COMPLETED 时不要改写 action 或 actionParams，网页端会据此高亮设备。
            当用户要求查看某应用下的托管图层时，调用 viewHostedLayer，并分别传入应用名称和图层名称。
            当用户要求查看组织范围时，调用 drawGeometryRegion 并将 bufferDistanceMeters 设为0；
            当用户要求查看设备缓冲范围时，将用户给出的米数原样传入 bufferDistanceMeters。
            当用户要求查看基建工程时，调用 browseConstructionProject；多候选时先确认，唯一结果默认加载第一个模型。

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

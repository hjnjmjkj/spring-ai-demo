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

            地图场景按以下规则调用工具：

            1. 设备定位
               用户要求定位线路、变电站等设备时，调用 locateEquipment。
               keyword 传设备名称、OID或设备ID。COMPLETED 时保留 LOCATE_EQUIPMENT action 和 actionParams，
               由网页端定位并高亮设备。

            2. 托管图层查看
               用户要求查看某个应用下的托管图层时，调用 viewHostedLayer。
               applicationName 传应用名称，layerName 传图层名称。COMPLETED 时保留 LOAD_HOSTED_LAYER action
               和 actionParams，由网页端在三维场景加载图层。

            3. 几何区域绘制
               用户要求查看组织机构范围时，调用 drawGeometryRegion，target 传组织名称，
               bufferDistanceMeters 必须传0。用户要求查看设备缓冲范围时，target 传设备名称或OID，
               bufferDistanceMeters 原样传入用户指定的米数。COMPLETED 时保留 DRAW_REGION action
               和 actionParams，由网页端绘制GeoJSON区域。

            4. 基建工程浏览
               用户要求查看基建工程时，调用 browseConstructionProject。
               projectNameOrId 传工程名称或工程ID。COMPLETED 时保留 SHOW_CONSTRUCTION_PROJECT action
               和 actionParams，由网页端加载工程及默认模型。

            所有地图工具都必须按 status 处理结果：
            - COMPLETED：不得改写或自行构造 action、actionParams，直接交给网页端执行。
            - NEED_CONFIRMATION：列出 candidates 让用户选择，不得擅自选择，也不要继续执行地图动作。
            - NOT_FOUND：说明未找到结果，并请用户提供更准确的名称或ID。

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

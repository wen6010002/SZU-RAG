package com.szu.rag.rag.vector;

import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MilvusVectorStoreService implements VectorStoreService {

    @Value("${milvus.host:localhost}")
    private String host;
    @Value("${milvus.port:19530}")
    private int port;

    private MilvusClientV2 client;

    @PostConstruct
    public void init() {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .build();
        client = new MilvusClientV2(config);
        log.info("Milvus client connected to {}:{}", host, port);
    }

    @Override
    public void createCollection(String collectionName, int dimension) {
        HasCollectionReq hasReq = HasCollectionReq.builder().collectionName(collectionName).build();
        boolean exists = client.hasCollection(hasReq);
        if (exists) {
            log.info("Collection {} already exists", collectionName);
            return;
        }

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder().fieldName("id").dataType(DataType.Int64).isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName("vector").dataType(DataType.FloatVector).dimension(dimension).build());
        schema.addField(AddFieldReq.builder().fieldName("document_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("chunk_index").dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder().fieldName("chunk_text").dataType(DataType.VarChar).maxLength(65535).build());
        schema.addField(AddFieldReq.builder().fieldName("source_title").dataType(DataType.VarChar).maxLength(1024).build());
        schema.addField(AddFieldReq.builder().fieldName("source_url").dataType(DataType.VarChar).maxLength(1024).build());

        List<IndexParam> indexes = List.of(
                IndexParam.builder().fieldName("vector")
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.COSINE).build()
        );

        CreateCollectionReq request = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexes)
                .build();
        client.createCollection(request);
        log.info("Created Milvus collection: {}", collectionName);
    }

    @Override
    public void insert(String collectionName, List<Long> ids, List<List<Float>> vectors, List<Map<String, Object>> metadataList) {
        List<JsonObject> data = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Map<String, Object> meta = metadataList.get(i);
            JsonObject row = new JsonObject();
            row.addProperty("id", ids.get(i));
            row.add("vector", com.google.gson.JsonParser.parseString(
                    vectors.get(i).stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"))));
            row.addProperty("document_id", ((Number) meta.getOrDefault("document_id", 0)).longValue());
            row.addProperty("chunk_index", ((Number) meta.getOrDefault("chunk_index", 0)).intValue());
            row.addProperty("chunk_text", (String) meta.getOrDefault("chunk_text", ""));
            row.addProperty("source_title", (String) meta.getOrDefault("source_title", ""));
            row.addProperty("source_url", (String) meta.getOrDefault("source_url", ""));
            data.add(row);
        }
        client.insert(InsertReq.builder().collectionName(collectionName).data(data).build());
    }

    @Override
    public void delete(String collectionName, List<Long> ids) {
        List<Object> idList = new ArrayList<>(ids);
        client.delete(DeleteReq.builder().collectionName(collectionName).ids(idList).build());
    }

    @Override
    public List<SearchResult> search(String collectionName, List<Float> queryVector, int topK, float scoreThreshold) {
        SearchResp resp = client.search(SearchReq.builder()
                .collectionName(collectionName)
                .data(List.of(new FloatVec(queryVector)))
                .topK(topK)
                .outputFields(List.of("document_id", "chunk_index", "chunk_text", "source_title", "source_url"))
                .build());

        List<SearchResult> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = resp.getSearchResults();
        if (!searchResults.isEmpty()) {
            for (SearchResp.SearchResult hit : searchResults.get(0)) {
                float score = hit.getScore();
                if (score >= scoreThreshold) {
                    results.add(new SearchResult(
                            (Long) hit.getId(),
                            score,
                            hit.getEntity()
                    ));
                }
            }
        }
        return results;
    }
}

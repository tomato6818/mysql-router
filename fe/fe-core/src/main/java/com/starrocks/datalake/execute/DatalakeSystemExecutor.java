package com.starrocks.datalake.execute;

import com.starrocks.datalake.ast.*;
import com.starrocks.datalake.parser.DatalakeSystemParser;
import com.starrocks.mysql.MysqlCommand;
import com.starrocks.qe.ConnectContext;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class DatalakeSystemExecutor implements DatalakeExecutor {
    ConnectContext ctx;
    DatalakeSystemParser datalakeSystemParser;
    CustomObjectsApi api;
    String namespace = "test";
    List<String> starrockscluster;
    public DatalakeSystemExecutor(ConnectContext ctx) {
        this.ctx = ctx;
        this.datalakeSystemParser = new DatalakeSystemParser();
        // 1. Kubeconfig 파일에서 Kubernetes 클라이언트 설정 로드
        String kubeConfigPath = System.getProperty("user.home") + "/.kube/config";
        ApiClient client = null;
        try {
            client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
            Configuration.setDefaultApiClient(client);

            // 2. CustomObjectsApi 인스턴스 생성
            this.api = new CustomObjectsApi();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.starrockscluster = new ArrayList<String>();
    }
    @Override
    public void execute(MysqlCommand command, String query, ByteBuffer packetBuf) throws IOException {
        SystemNode parseStmt = datalakeSystemParser.parse(query+";");
        List<SystemNode> nodes = parseStmt.getList();
        for (SystemNode node: nodes) {
            if (node instanceof CreateStarrocksStatement) {
                createStarrocks(node);
                ctx.getMysqlChannel().realNetSend(ctx.ok());
            } else if (node instanceof SelectStatement) {
                System.out.println("SelectStatement");
                selectStarrocks(node);
                List<ByteBuffer> packets = ctx.resultSend("gre", starrockscluster.get(0));

                for (ByteBuffer packet : packets) {
                    System.out.println(Arrays.toString(packet.array()));
                    packet.rewind();
                    ctx.getMysqlChannel().realNetSend(packet);


                    System.out.println();
                }
            }

        }
        System.out.println("System parseStmt:"+((ProgramStatement)parseStmt).getList().get(0));
    }

    private int selectStarrocks(SystemNode node) {
        SelectStatement css = (SelectStatement) node;

        System.out.println("selectStarrocks:" + css.getTable());
        return 0;
    }

    private int createStarrocks(SystemNode node) {
        CreateStarrocksStatement css = (CreateStarrocksStatement) node;
        System.out.println("name:" + css.getName()+" size:" + css.getSize() + " fe[" + css.getFeSpec()+"] cn[" + css.getCnSpec()+"]");

        // 3. 제공된 YAML 구조를 Java Map 객체로 변환
        Map<String, Object> starrocksCluster = new HashMap<>();
        starrocksCluster.put("apiVersion", "starrocks.com/v1");
        starrocksCluster.put("kind", "StarRocksCluster");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", css.getName());
        metadata.put("namespace", namespace); // 네임스페이스를 'starrocks'로 지정
        starrocksCluster.put("metadata", metadata);

        Map<String, Object> spec = new HashMap<>();

        // starRocksBeSpec (BE 설정)
        Map<String, Object> beSpec = new HashMap<>();
        beSpec.put("resolveKey", "be.conf");
        beSpec.put("image", "starrocks/be-ubuntu:3.5.2");
        beSpec.put("imagePullPolicy", "IfNotPresent");

        Map<String, Object> beResources = new HashMap<>();
        Map<String, String> beLimits = new HashMap<>();
        beLimits.put("cpu", "500m");
        beLimits.put("memory", "4Gi");
        beResources.put("limits", beLimits);

        Map<String, String> beRequests = new HashMap<>();
        beRequests.put("cpu", "500m");
        beRequests.put("memory", "4Gi");
        beResources.put("requests", beRequests);
        beSpec.put("resources", beResources);

        beSpec.put("replicas", 3);

        Map<String, String> beService = new HashMap<>();
        beService.put("type", "ClusterIP");
        beSpec.put("service", beService);
        spec.put("starRocksBeSpec", beSpec);


        // starRocksFeSpec (FE 설정)
        Map<String, Object> feSpec = new HashMap<>();
        feSpec.put("image", "starrocks/fe-ubuntu:3.5.2");
        feSpec.put("imagePullPolicy", "IfNotPresent");

        Map<String, Object> feResources = new HashMap<>();
        Map<String, String> feLimits = new HashMap<>();
        feLimits.put("cpu", "500m");
        feLimits.put("memory", "4Gi");
        feResources.put("limits", feLimits);

        Map<String, String> feRequests = new HashMap<>();
        feRequests.put("cpu", "500m");
        feRequests.put("memory", "4Gi");
        feResources.put("requests", feRequests);
        feSpec.put("resources", feResources);

        feSpec.put("replicas", 3);

        Map<String, String> feService = new HashMap<>();
        feService.put("type", "ClusterIP");
        feSpec.put("service", feService);
        spec.put("starRocksFeSpec", feSpec);

        starrocksCluster.put("spec", spec);

        String group = "starrocks.com";
        String version = "v1";
        String namespace = (String) metadata.get("namespace");
        String plural = "starrocksclusters";

        Object body = starrocksCluster;

        String pretty = "true"; // 응답을 보기 좋게 포맷팅
        String dryRun = null; // dry-run 모드 비활성화 (null)
        String fieldManager = "starrocks-java-client"; // 필드 관리자 이름 지정
        System.out.println("StarRocksCluster 커스텀 리소스 생성 시작...");

        try {
            // 4. API 서버에 Custom Resource 생성 요청
            Object result = api.createNamespacedCustomObject(
                    group,
                    version,
                    namespace,
                    plural,
                    body,
                    pretty,
                    dryRun,
                    fieldManager
            );
            System.out.println("StarRocksCluster 'kube-starrocks' 생성 요청 완료.");
            System.out.println("결과: " + result);
            starrockscluster.add(css.getName());
        } catch (ApiException e) {
            System.err.println("API 예외 발생: " + e.getMessage());
            System.err.println("HTTP 상태 코드: " + e.getCode());
            System.err.println("응답 바디: " + e.getResponseBody()); // 이 부분을 추가하세요
        }

        return 0;
    }
}

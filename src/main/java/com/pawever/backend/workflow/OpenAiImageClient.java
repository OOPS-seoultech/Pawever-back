package com.pawever.backend.workflow;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;

@Component
public class OpenAiImageClient {
  private final ImageGenerationProperties config;
  private final RestTemplate http;
  private final ObjectMapper json=new ObjectMapper();
  public record Input(String name,byte[] bytes,String type) {}
  public record Result(byte[] sheet,List<byte[]> views,String usage,String requestId) {}
  public OpenAiImageClient(ImageGenerationProperties config) {
    this.config=config;
    var factory=new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(20));factory.setReadTimeout(Duration.ofMinutes(5));
    http=new RestTemplate(factory);
  }
  public boolean connectionTest() {
    if (!config.ready()) return false;
    var headers=new HttpHeaders();headers.setBearerAuth(config.getApiKey());
    try {return http.exchange(config.getBaseUrl()+"/models/"+config.getModel(),HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode().is2xxSuccessful();}
    catch(RestClientException e) {return false;}
  }
  public Result generate(List<Input> inputs,String prompt) {
    if(!config.ready()) throw new WorkflowException(503,"IMAGE_API_NOT_CONFIGURED","이미지 API 설정을 확인해 주세요.");
    var body=new LinkedMultiValueMap<String,Object>();
    body.add("model",config.getModel());body.add("prompt",prompt);body.add("n","1");
    body.add("size","2048x2048");body.add("quality","high");body.add("output_format","png");
    for(var input:inputs) {
      if(input.bytes().length>50L*1024*1024) throw new WorkflowException(422,"INPUT_IMAGE_TOO_LARGE","참조 사진이 너무 큽니다.");
      var file=new ByteArrayResource(input.bytes()) { @Override public String getFilename(){return input.name();} };
      var headers=new HttpHeaders();headers.setContentType(MediaType.parseMediaType(input.type()));
      body.add("image[]",new HttpEntity<>(file,headers));
    }
    var headers=new HttpHeaders();headers.setBearerAuth(config.getApiKey());headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    try {
      var response=http.postForEntity(config.getBaseUrl()+"/images/edits",new HttpEntity<>(body,headers),String.class);
      var result=json.readTree(response.getBody());
      var encoded=result.path("data").path(0).path("b64_json").asText();
      if(encoded.isBlank()||encoded.length()>40*1024*1024) throw new IOException("Missing or oversized image");
      byte[] sheet=Base64.getDecoder().decode(encoded);
      return new Result(sheet,split(sheet),result.path("usage").toString(),response.getHeaders().getFirst("x-request-id"));
    } catch(RestClientResponseException e) {
      // Do not store API response bodies: they may echo input or credentials.
      throw new WorkflowException(502,"OPENAI_HTTP_"+e.getStatusCode().value(),"이미지 생성 API가 요청을 거절했습니다. 결제·모델 권한·사용량을 확인해 주세요.");
    } catch(ResourceAccessException e) {
      throw new WorkflowException(502,"IMAGE_RESULT_UNKNOWN","API 결과를 확인하지 못했습니다. 중복 과금을 막기 위해 자동 재생성하지 않습니다.");
    } catch(RestClientException|IOException|IllegalArgumentException e) {
      throw new WorkflowException(502,"IMAGE_OUTPUT_INVALID","생성된 이미지 형식을 확인하지 못했습니다.");
    }
  }
  static List<byte[]> split(byte[] bytes) throws IOException {
    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Not an image");
      var reader=readers.next();
      try {
        reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);
        if(w!=h||w<1024||w>4096||w%2!=0)throw new IOException("Invalid 2x2 sheet dimensions");
        var sheet=reader.read(0);var result=new ArrayList<byte[]>();
        for(int i=0;i<4;i++){var output=new ByteArrayOutputStream();ImageIO.write(sheet.getSubimage((i%2)*w/2,(i/2)*h/2,w/2,h/2),"png",output);result.add(output.toByteArray());}
        sheet.flush();return result;
      } finally {reader.dispose();}
    }
  }
}

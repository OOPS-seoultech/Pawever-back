package com.pawever.backend.workflow;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class ImageGenerationWorker {
  private final ImageGenerationService images;
  private final ImageGenerationProperties config;
  private final ExecutorService executor=Executors.newSingleThreadExecutor();
  private final AtomicBoolean running=new AtomicBoolean();
  @Scheduled(fixedDelay=5000)
  public void tick(){
    if(!config.ready()||!config.isWorkerEnabled()||!running.compareAndSet(false,true))return;
    executor.submit(()->{try{images.runOne();}finally{running.set(false);}});
  }
  @PreDestroy public void close(){executor.shutdownNow();}
}

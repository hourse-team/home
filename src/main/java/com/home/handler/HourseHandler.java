package com.home.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.home.model.*;
import com.home.repository.HourseRepository;
import com.home.util.ServerResponseUtil;
import com.home.vo.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerRequestExtensionsKt;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sun.misc.BASE64Decoder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.springframework.web.reactive.function.BodyInserters.fromObject;

/**
 * Created by Administrator on 2017/8/20.
 */
@Component
public class HourseHandler {
    @Autowired
    HourseRepository hourseRepository;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(HourseHandler.class);

    public static final ApiResponse noData = new ApiResponse(202,"page parameter error",Collections.EMPTY_LIST);

    public static final NoPagingResponse error = NoPagingResponse.error("参数不对，服务器拒绝响应");

    public Mono<ServerResponse> getHourses(ServerRequest request){
        Sort sort = new Sort(Sort.Direction.DESC,"createDate");
        Mono<PageRequest> page = request.bodyToMono(PageRequest.class).switchIfEmpty(Mono.just(new PageRequest()));
        Flux<BaseHourse> hourses = hourseRepository.findByCreateByOrIsPublic(sort,request.pathVariable("userId"),"0")
                .filter(res -> {
                    String title = page.block().getName();
                    boolean bool;
                    if(title == null || title.equals("")){
                        bool = 1 == 1;
                    } else {
                        bool = res.getTitle().contains(title);
                    }
                    return bool;
                });
        Mono<ApiResponse> build = ApiResponse.build(hourses.count(), hourses.collectList().zipWith(page, (list, pag) -> {
            Integer start = pag.getPageNumber()*pag.getPageSize();
            Integer end = (pag.getPageNumber()+1)*pag.getPageSize();
            list = end > list.size() ? list.subList(start,list.size()) : list.subList(start,end);
            return list;
        }),page).onErrorReturn(noData);
        return ServerResponseUtil.createByMono(build,ApiResponse.class);
    }

    public Mono<ServerResponse> getHourse(ServerRequest request){
        return hourseRepository.findById(request.pathVariable("hourseId"))
                .flatMap(data -> ServerResponseUtil.createResponse(NoPagingResponse.success(data)))
                .switchIfEmpty(ServerResponseUtil.createResponse(NoPagingResponse.noFound()));
    }

    public Mono<ServerResponse> findHourse(ServerRequest request){
        return hourseRepository.findById(request.pathVariable("hourseId"))
                .flatMap(data ->
                    ServerResponse.ok().contentType(MediaType.APPLICATION_JSON_UTF8)
                            .body(fromObject(new NoPagingResponse(200,"success",data)))
                );
    }

    public Mono<ServerResponse> update(ServerRequest request){
        String type = request.queryParam("type").get();
        Class<? extends BaseHourse> clazz = type.equals("1") ? DealHourse.class : RentHouse.class;
//        return hourseRepository.save(request.bodyToMono(clazz))
//                .flatMap(data -> ServerResponseUtil.createResponse(NoPagingResponse.success(data)))
//                .onErrorResume(throwable -> ServerResponseUtil.error());
        return request.bodyToMono(clazz).flatMap(hourse -> hourseRepository.save(hourse))
                .flatMap(data -> ServerResponseUtil.createResponse(NoPagingResponse.success(data)))
                .onErrorResume(throwable -> ServerResponseUtil.error()).switchIfEmpty(ServerResponseUtil.error());
    }

    public Mono<ServerResponse> delete(ServerRequest request){
        Mono<BaseHourse> hourse = hourseRepository.findById(request.pathVariable("hourseId")).map(res -> {
            res.setIsDeleted("1");
            res.setUpdateBy(request.bodyToMono(User.class).map(User::getId));
            return res;
        });
        return ServerResponseUtil.createByMono(hourse.flatMap(data -> hourseRepository.save(data))
                .flatMap(r -> Mono.just(NoPagingResponse.success(r)))
                .onErrorReturn(error),NoPagingResponse.class);
    }

    public Mono<ServerResponse> create(ServerRequest request){
        String type = request.queryParam("type").get();
        Class<? extends BaseHourse> clazz = type.equals("1") ? DealHourse.class : RentHouse.class;
        Mono<BaseHourse> hourseMono = request.bodyToMono(clazz).map(hourse -> {
            hourse.setCreateDate(new Date());
            hourse.setType(type);
            hourse.setIsDeleted("0");
            return hourse;
        });
        return ServerResponseUtil.createByMono(hourseMono.flatMap(data -> hourseRepository.save(data))
                .flatMap(r -> Mono.just(NoPagingResponse.success(r)))
                .onErrorReturn(error),NoPagingResponse.class);
    }

    public Mono<ServerResponse> getAllHourses(ServerRequest request){
        String type = request.pathVariable("type");
        Integer pageSize = Integer.valueOf(request.queryParam("pageSize").orElse("10"));
        Integer pageNumber = Integer.valueOf(request.queryParam("pageNumber").orElse("0"));
        Sort sort = new Sort(Sort.Direction.DESC,"createDate");
        return hourseRepository.findByTypeAndIsDeleted(sort,type,"0")
                .collectList().map(list -> {
                    Integer start = pageNumber*pageSize;
                    Integer end = (pageNumber+1)*pageSize;
                    list = end > list.size() ? list.subList(start,list.size()) : list.subList(start,end);
                    return list;
                }).flatMap(data -> ServerResponseUtil.createResponse(FrontResponse.success(
                        new FrontData(data.size(),pageNumber,pageSize,data))))
                .onErrorResume(throwable -> ServerResponseUtil.error());
    }
}

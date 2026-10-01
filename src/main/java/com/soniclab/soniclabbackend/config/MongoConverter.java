package com.soniclab.soniclabbackend.config;

import org.springframework.core.convert.converter.Converter;

/**
 * Interface marcadora para conversores customizados do MongoDB.
 * Permite que cada módulo (user, soundkit, etc.) registre seus próprios conversores
 * como Spring Beans de forma totalmente desacoplada.
 */
public interface MongoConverter<S, T> extends Converter<S, T> {
}

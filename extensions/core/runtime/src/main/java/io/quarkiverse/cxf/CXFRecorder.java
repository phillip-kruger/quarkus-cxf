package io.quarkiverse.cxf;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.cxf.devconsole.DevCxfServerInfosSupplier;
import io.quarkiverse.cxf.transport.CxfHandler;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.HttpConfiguration;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class CXFRecorder {
    private static final Logger LOGGER = Logger.getLogger(CXFRecorder.class);
    private static final String DEFAULT_EP_ADDR = "http://localhost:8080";

    /**
     * Create CXFClientInfo supplier.
     * <p>
     * This method is called once per @WebService *interface*. The idea is to produce a default client config for a
     * given SEI.
     */
    public RuntimeValue<CXFClientInfo> cxfClientInfoSupplier(CXFClientData cxfClientData) {
        return new RuntimeValue<>(new CXFClientInfo(
                cxfClientData.getSei(),
                format("%s/%s", DEFAULT_EP_ADDR, cxfClientData.getSei().toLowerCase()),
                cxfClientData.getSoapBinding(),
                cxfClientData.getWsNamespace(),
                cxfClientData.getWsName(),
                cxfClientData.getClassNames()));
    }

    private class ServletConfig {
        public CxfEndpointConfig config;
        public String path;

        public ServletConfig(CxfEndpointConfig cxfEndPointConfig, String relativePath) {
            this.config = cxfEndPointConfig;
            this.path = relativePath;
        }
    }

    public void registerCXFServlet(RuntimeValue<CXFServletInfos> runtimeInfos, String path, String sei,
            CxfConfig cxfConfig, String serviceName, String serviceTargetNamepsace, String soapBinding,
            List<String> wrapperClassNames, String wsImplementor, Boolean isProvider) {
        CXFServletInfos infos = runtimeInfos.getValue();
        Map<String, List<ServletConfig>> implementorToCfg = new HashMap<>();
        for (Map.Entry<String, CxfEndpointConfig> webServicesByPath : cxfConfig.endpoints.entrySet()) {
            CxfEndpointConfig cxfEndPointConfig = webServicesByPath.getValue();
            String relativePath = webServicesByPath.getKey();
            if (!cxfEndPointConfig.implementor.isPresent()) {
                continue;
            }
            String cfgImplementor = cxfEndPointConfig.implementor.get();
            List<ServletConfig> lst;
            if (implementorToCfg.containsKey(cfgImplementor)) {
                lst = implementorToCfg.get(cfgImplementor);
            } else {
                lst = new ArrayList<>();
                implementorToCfg.put(cfgImplementor, lst);
            }
            lst.add(new ServletConfig(cxfEndPointConfig, relativePath));
        }
        List<ServletConfig> cfgs = implementorToCfg.get(wsImplementor);
        if (cfgs != null) {
            for (ServletConfig cfg : cfgs) {
                CxfEndpointConfig cxfEndPointConfig = cfg.config;
                String relativePath = cfg.path;
                startRoute(path, sei, serviceName, serviceTargetNamepsace, soapBinding,
                        wrapperClassNames, wsImplementor, infos,
                        cxfEndPointConfig, relativePath, isProvider);
            }
        } else {
            if (serviceName == null || serviceName.isEmpty()) {
                serviceName = sei.toLowerCase();
                if (serviceName.contains(".")) {
                    serviceName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
                }
            }
            String relativePath = "/" + serviceName;
            startRoute(path, sei, serviceName, serviceTargetNamepsace, soapBinding, wrapperClassNames,
                    wsImplementor, infos, null, relativePath, isProvider);
        }
    }

    private void startRoute(String path, String sei, String serviceName, String serviceTargetNamespace,
            String soapBinding, List<String> wrapperClassNames, String wsImplementor,
            CXFServletInfos infos, CxfEndpointConfig cxfEndPointConfig, String relativePath, Boolean isProvider) {
        if (wsImplementor != null && !wsImplementor.equals("")) {
            CXFServletInfo cfg = new CXFServletInfo(path,
                    relativePath,
                    wsImplementor,
                    sei,
                    cxfEndPointConfig != null ? cxfEndPointConfig.wsdlPath.orElse(null) : null,
                    serviceName,
                    serviceTargetNamespace,
                    cxfEndPointConfig != null ? cxfEndPointConfig.soapBinding.orElse(soapBinding) : soapBinding,
                    wrapperClassNames,
                    isProvider,
                    cxfEndPointConfig != null ? cxfEndPointConfig.publishedEndpointUrl.orElse(null) : null);
            if (cxfEndPointConfig != null && cxfEndPointConfig.inInterceptors.isPresent()) {
                cfg.getInInterceptors().addAll(cxfEndPointConfig.inInterceptors.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.outInterceptors.isPresent()) {
                cfg.getOutInterceptors().addAll(cxfEndPointConfig.outInterceptors.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.outFaultInterceptors.isPresent()) {
                cfg.getOutFaultInterceptors().addAll(cxfEndPointConfig.outFaultInterceptors.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.inFaultInterceptors.isPresent()) {
                cfg.getInFaultInterceptors().addAll(cxfEndPointConfig.inFaultInterceptors.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.features.isPresent()) {
                cfg.getFeatures().addAll(cxfEndPointConfig.features.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.handlers.isPresent()) {
                cfg.getHandlers().addAll(cxfEndPointConfig.handlers.get());
            }
            LOGGER.trace("register CXF Servlet info");
            infos.add(cfg);
        }
    }

    public RuntimeValue<CXFServletInfos> createInfos() {
        CXFServletInfos infos = new CXFServletInfos();
        return new RuntimeValue<>(infos);
    }

    public Handler<RoutingContext> initServer(RuntimeValue<CXFServletInfos> infos, BeanContainer beanContainer,
            HttpConfiguration httpConfiguration) {
        LOGGER.trace("init server");
        // There may be a better way to handle this
        DevCxfServerInfosSupplier.setServletInfos(infos.getValue());
        return new CxfHandler(infos.getValue(), beanContainer, httpConfiguration);
    }

    public void setPath(RuntimeValue<CXFServletInfos> infos, String path, String contextPath) {
        infos.getValue().setPath(path);
        infos.getValue().setContextPath(contextPath);
    }
}
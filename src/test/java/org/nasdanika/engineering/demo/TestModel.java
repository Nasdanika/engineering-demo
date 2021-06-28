package org.nasdanika.engineering.demo;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import org.eclipse.emf.common.util.URI;
import org.junit.Test;
import org.nasdanika.common.CommandFactory;
import org.nasdanika.common.Context;
import org.nasdanika.common.Diagnostic;
import org.nasdanika.common.DiagnosticException;
import org.nasdanika.common.DiagramGenerator;
import org.nasdanika.common.MarkdownHelper;
import org.nasdanika.common.MutableContext;
import org.nasdanika.common.PrintStreamProgressMonitor;
import org.nasdanika.common.ProgressMonitor;
import org.nasdanika.common.Status;
import org.nasdanika.common.SupplierFactory;
import org.nasdanika.common.Util;
import org.nasdanika.common.persistence.ObjectLoader;
import org.nasdanika.common.persistence.SourceResolver;
import org.nasdanika.common.persistence.SourceResolver.Link;
import org.nasdanika.emf.persistence.EObjectLoader;
import org.nasdanika.engineering.gen.GenerateSiteConsumerFactory;
import org.nasdanika.html.app.Action;
import org.nasdanika.html.app.factories.BootstrapContainerApplicationSupplierFactory;
import org.nasdanika.html.app.factories.ComposedLoader;
import org.nasdanika.html.emf.SimpleEObjectViewAction;
import org.nasdanika.html.model.app.AppPackage;

/**
 * Tests of descriptor view parts and wizards.
 * @author Pavel
 *
 */
public class TestModel {
	
	private static final String TEST_RESOURCES_PREFIX = "engineering-demo/target/test-classes/";
	
	@Test
	public void testGenerateDemoSite() throws Exception {
		ObjectLoader loader = new EObjectLoader(new ComposedLoader(), null, AppPackage.eINSTANCE);
		
		ProgressMonitor progressMonitor = new PrintStreamProgressMonitor();
		String resourceName = "org/nasdanika/html/app/templates/cerulean/dark-fluid.yml";
		BootstrapContainerApplicationSupplierFactory applicationSupplierFactory = (BootstrapContainerApplicationSupplierFactory) loader.loadYaml(getClass().getClassLoader().getResource(resourceName), progressMonitor);
		
		URI modelURI = URI.createURI(getClass().getResource("/demo.yml").toString());
		GenerateSiteConsumerFactory consumerFactory = new GenerateSiteConsumerFactory(
				Collections.singleton(modelURI), 
				applicationSupplierFactory, 
				new File("docs")) {
			
			@Override
			protected MutableContext forkContext(Context context, ProgressMonitor progressMonitor) {
				MutableContext ret = super.forkContext(context, progressMonitor);

				MarkdownHelper markdownHelper = new MarkdownHelper() {
					
					@Override
					protected DiagramGenerator getDiagramGenerator() {
						return context.get(DiagramGenerator.class, DiagramGenerator.INSTANCE);
					}
					
				};
				ret.register(MarkdownHelper.class, markdownHelper);
				
				return ret;
			}
			
//			@Override
//			protected List<URL> getAppearanceLocations() {
//				return Collections.singletonList(getClass().getResource("/appearance.yml"));
//			}
			
		};
		
		Object actionFactory = loader.loadYaml(getClass().getResource("/site.yml"), progressMonitor);
		SupplierFactory<Action> asf = Util.<Action>asSupplierFactory(actionFactory);		
		
		CommandFactory commandFactory = asf.then(consumerFactory); 
		MutableContext context = Context.EMPTY_CONTEXT.fork();
		context.put(Context.BASE_URI_PROPERTY, "random://" + UUID.randomUUID() + "/" + UUID.randomUUID() + "/");
		context.put(SimpleEObjectViewAction.DOC_URI, "https://docs.nasdanika.org/engineering/engineering/");
		context.register(Date.class, new Date());

		URI uri = URI.createFileURI(new File(".").getCanonicalPath());
		SourceResolver sourceResolver = marker -> {
			if (marker != null && !Util.isBlank(marker.getLocation())) { 
				try {
					File locationFile = new File(new java.net.URI(marker.getLocation()));
					URI locationURI = URI.createFileURI(locationFile.getCanonicalPath());
					URI relativeLocationURI = locationURI.deresolve(uri, true, true, true); 
					String relativeLocationString = relativeLocationURI.toString();
					return new Link() {
	
						@Override
						public String getLocation() {
							if (relativeLocationString.startsWith(TEST_RESOURCES_PREFIX)) {
								return "https://github.com/Nasdanika/engineering-demo/blob/main/src/test/resources/" + relativeLocationString.substring(TEST_RESOURCES_PREFIX.length()) + "#L" + marker.getLine();
							}
							return marker.getLocation();
						}
						
						@Override
						public String getText() {
							String path = relativeLocationString;
							if (path.startsWith(TEST_RESOURCES_PREFIX)) {
								path = "src/test/resources/" + relativeLocationString.substring(TEST_RESOURCES_PREFIX.length());										
							}
							return path + " " + marker.getLine() + ":" + marker.getColumn();
						}
						
					};
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			return null;
		};
		
		context.register(SourceResolver.class, sourceResolver);
		
		try {
			Diagnostic diagnostic = Util.call(commandFactory.create(context), progressMonitor);
			if (diagnostic.getStatus() == Status.WARNING || diagnostic.getStatus() == Status.ERROR) {
				System.err.println("***********************");
				System.err.println("*      Diagnostic     *");
				System.err.println("***********************");
				diagnostic.dump(System.err, 4, Status.ERROR, Status.WARNING);
			}
		} catch (DiagnosticException e) {
			System.err.println("******************************");
			System.err.println("*      Diagnostic failed     *");
			System.err.println("******************************");
			e.getDiagnostic().dump(System.err, 4, Status.FAIL);
			throw e;
		}
	}

}

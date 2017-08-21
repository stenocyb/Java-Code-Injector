package com.github.gift;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.zeroturnaround.zip.ZipUtil;

import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

public class Gift 
{
	
	public static void main(String[] args) throws Exception
	{
		if(args.length == 2)
		{
			String jarPath = args[0];
			String injectPath = args[1];
			
			injectPayload(new File(jarPath), injectPath);
			
			ClassPool pool = ClassPool.getDefault();
			ClassPath classPath = pool.insertClassPath(args[0]);
			
			JarFile modFile = new JarFile(jarPath);
			Manifest mf = modFile.getManifest();
			Attributes attr = mf.getMainAttributes();
			
			modFile.close();
			
			String className = attr.getValue(Attributes.Name.MAIN_CLASS);
			
			System.out.println(className);
			
			CtClass mainClass = pool.get(className);

			CtMethod customMethod = CtNewMethod.make("public static void injectCode() { new " + injectPath.replace(".class", "") + "(); }", mainClass);
			mainClass.addMethod(customMethod); 
			
			CtMethod method = mainClass.getDeclaredMethod("main");
			method.insertBefore("injectCode();");
			
			byte[] b = mainClass.toBytecode();
			pool.removeClassPath(classPath);
			
			StringBuilder sb = new StringBuilder();
			
			char[] charObjectArray = mainClass.getName().toCharArray();
			
			for(char c : charObjectArray)
			{
				sb.append(c == '.' ? "/" : c);
			}
			
			replaceJarFile(new File(jarPath), b, sb.toString() + ".class");
		}
		else
		{
			System.err.println("Error: You have to specify a jar file.");
			System.out.println("\nSyntax: Gift.jar <inputJar> <classToInject>");
		}
	}

	public static void injectPayload(File jarFile, String injectClass) throws IOException, URISyntaxException
	{
		File tempFolder = new File(jarFile.getParentFile(), "tmp/");
        tempFolder.mkdirs();
                
        ZipUtil.unpack(jarFile, tempFolder);
        
        /*
        File metaInf = new File(tempFolder, "META-INF");
        File manifest = new File(metaInf, "MANIFEST.MF");
        
        if(manifest.exists())
        {
        	manifest.delete();
        	metaInf.delete();
        }
        */
        
        try
        {
        	Path fromFile = new File(injectClass).getAbsoluteFile().toPath();
        	Path toFile = new File(tempFolder, injectClass).getAbsoluteFile().toPath();
        	
        	Files.copy(fromFile, toFile);
        }
        catch(IOException e)
        {
        	e.printStackTrace();
        }
        
        jarFile.delete();
        ZipUtil.pack(tempFolder, jarFile);
        
        delete(tempFolder);
        
        tempFolder.delete();
	}
	
	private static void delete(File file) throws FileNotFoundException
	{
		if(file.isDirectory())
		{
		    for (File c : file.listFiles())
		    {
		    	delete(c);
		    }
		}
		
		if (!file.delete())
		{
		    throw new FileNotFoundException("Unable to delete: " + file.getAbsolutePath());
		}
	}
	
	public static void replaceJarFile(File jarFile, byte[] byteCode, String outputFileName) throws IOException
	{
		File tempJarFile = new File(jarFile.getAbsolutePath() + ".tmp");
		JarFile jar = new JarFile(jarFile);
		
		boolean success = false;

		try
		{
			JarOutputStream tempJar = new JarOutputStream(new FileOutputStream(tempJarFile));

			byte[] buffer = new byte[1024];
			int bytesRead;

			try
			{
				try 
				{
					JarEntry entry = new JarEntry(outputFileName);
					tempJar.putNextEntry(entry);
					tempJar.write(byteCode);
				} 
				catch (Exception e) 
				{
					e.printStackTrace();

					tempJar.putNextEntry(new JarEntry("stub"));
				}

				InputStream entryStream = null;
				
				for(Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) 
				{
					JarEntry entry = (JarEntry) entries.nextElement();

					if(! entry.getName().equals(outputFileName)) 
					{
						entryStream = jar.getInputStream(entry);
						tempJar.putNextEntry(entry);

						while((bytesRead = entryStream.read(buffer)) != -1) 
						{
							tempJar.write(buffer, 0, bytesRead);
						}
					}
				}
				
				if(entryStream != null)
				{
					entryStream.close();
				}
				
				success = true;
			}
			catch (Exception ex)
			{
				ex.printStackTrace();

				tempJar.putNextEntry(new JarEntry("stub"));
			}
			finally 
			{
				tempJar.close();
			}
		}
		finally 
		{
			jar.close();

			if(!success) 
			{
				tempJarFile.delete();
			}
		}


		if(success) 
		{            
			if(jarFile.delete())
			{
				tempJarFile.renameTo(jarFile);
			}
			else
			{
				System.out.println("Error: Unable to delete the jar file.");
			}
		}
	}
	
}

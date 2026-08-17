#!/usr/bin/env node

/**
 * Universal Java AST & Javadoc Schema Extractor
 *
 * Scans any Java project directory, parses Javadoc comments, annotations,
 * methods, parameters, and types, and exports a standardized documentation schema.
 */

const fs = require('fs');
const path = require('path');

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    src: '../../backend',
    out: 'src/data/default-schema.json',
    projectName: 'Filmpire Microservices',
    version: '1.0.0',
    description: 'Enterprise Microservices Platform for Movie & Actor Discovery',
  };

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--src' && args[i + 1]) options.src = args[++i];
    else if (args[i] === '--out' && args[i + 1]) options.out = args[++i];
    else if (args[i] === '--name' && args[i + 1]) options.projectName = args[++i];
    else if (args[i] === '--version' && args[i + 1]) options.version = args[++i];
  }
  return options;
}

function findJavaFiles(dir, fileList = []) {
  if (!fs.existsSync(dir)) return fileList;
  const entries = fs.readdirSync(dir, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (
        entry.name === 'build' ||
        entry.name === '.gradle' ||
        entry.name === 'node_modules' ||
        entry.name === 'test' ||
        entry.name === 'target' ||
        entry.name === '.git'
      ) {
        continue;
      }
      findJavaFiles(fullPath, fileList);
    } else if (entry.isFile() && entry.name.endsWith('.java')) {
      fileList.push(fullPath);
    }
  }
  return fileList;
}

function cleanJavadoc(rawDoc) {
  if (!rawDoc) {
    return {
      summary: '',
      description: '',
      tags: { params: [], returns: '', throws: [], see: [], since: '', notes: [], pitfalls: [] },
    };
  }
  
  const lines = rawDoc
    .replace(/^\/\*\*?/, '')
    .replace(/\*\/$/, '')
    .split('\n')
    .map(line => line.trim().replace(/^\*\s?/, ''))
    .filter(line => line.length > 0);

  let descriptionLines = [];
  const tags = {
    params: [],
    returns: '',
    throws: [],
    see: [],
    since: '',
    notes: [],
    pitfalls: []
  };

  for (const line of lines) {
    if (line.startsWith('@param')) {
      const match = line.match(/^@param\s+([A-Za-z0-9_]+)\s+(.*)$/);
      if (match) {
        tags.params.push({ name: match[1], description: match[2].trim() });
      }
    } else if (line.startsWith('@return')) {
      tags.returns = line.replace(/^@return\s*/, '').trim();
    } else if (line.startsWith('@throws') || line.startsWith('@exception')) {
      const match = line.match(/^@(throws|exception)\s+([A-Za-z0-9_.]+)\s*(.*)$/);
      if (match) {
        tags.throws.push({ exception: match[2], description: (match[3] || '').trim() });
      }
    } else if (line.startsWith('@see')) {
      tags.see.push(line.replace(/^@see\s*/, '').trim());
    } else if (line.startsWith('@since')) {
      tags.since = line.replace(/^@since\s*/, '').trim();
    } else if (line.startsWith('@note')) {
      tags.notes.push(line.replace(/^@note\s*/, '').trim());
    } else if (line.startsWith('@pitfall')) {
      tags.pitfalls.push(line.replace(/^@pitfall\s*/, '').trim());
    } else if (!line.startsWith('@')) {
      descriptionLines.push(line);
    }
  }

  const fullDescription = descriptionLines.join(' ');
  const summary = descriptionLines.length > 0 ? descriptionLines[0] : '';

  return {
    summary: summary.replace(/<[^>]*>/g, '').trim(),
    description: fullDescription.trim(),
    tags,
  };
}

function categorizeClass(className, packageName, annotations) {
  const name = className.toLowerCase();
  const pkg = packageName.toLowerCase();
  const annot = annotations.map(a => a.name.toLowerCase()).join(' ');

  if (annot.includes('controller') || name.endsWith('controller') || name.endsWith('facade') || pkg.includes('controller') || pkg.includes('facade')) {
    return 'Controllers & Endpoints';
  }
  if (annot.includes('service') || name.endsWith('service') || name.endsWith('usecase') || pkg.includes('service')) {
    return 'Services & Domain Logic';
  }
  if (annot.includes('repository') || name.endsWith('repository') || name.endsWith('dao') || pkg.includes('repository')) {
    return 'Repositories & Data Access';
  }
  if (annot.includes('entity') || annot.includes('document') || pkg.includes('model') || pkg.includes('entity')) {
    return 'Domain Models & Entities';
  }
  if (name.endsWith('dto') || name.endsWith('request') || name.endsWith('response') || pkg.includes('dto')) {
    return 'DTOs & Payload Records';
  }
  if (annot.includes('configuration') || name.endsWith('config') || pkg.includes('config')) {
    return 'Configurations & Infrastructure';
  }
  if (annot.includes('client') || name.endsWith('client') || name.endsWith('interceptor') || pkg.includes('client')) {
    return 'HTTP & API Clients';
  }
  if (annot.includes('component') || pkg.includes('security') || pkg.includes('mapper') || pkg.includes('util')) {
    return 'Components & Utilities';
  }
  return 'Core Types';
}

function parseJavaFile(filePath, rootSrc) {
  const content = fs.readFileSync(filePath, 'utf-8');
  const relPath = path.relative(rootSrc, filePath);
  const pathParts = relPath.split(path.sep);
  
  // Detect module name (e.g. "actor-service" or "backend/actor-service")
  let moduleName = 'core';
  for (const part of pathParts) {
    if (part.endsWith('-service') || part === 'shared-library' || part === 'api-gateway') {
      moduleName = part;
      break;
    }
  }

  // Package regex
  const pkgMatch = content.match(/package\s+([a-zA-Z0-9_.]+)\s*;/);
  const packageName = pkgMatch ? pkgMatch[1] : '';

  // Extract top-level Class/Interface/Record/Enum
  const typeRegex = /(?:\/\*\*([\s\S]*?)\*\/\s*)?((?:@[A-Za-z0-9_]+(?:\([^)]*\))?\s*)*)(?:public\s+|protected\s+|private\s+)?(?:static\s+|final\s+|abstract\s+)*(class|interface|record|enum)\s+([A-Za-z0-9_]+)(?:<[^>]+>)?(?:\s+extends\s+([A-Za-z0-9_.,\s<>]+))?(?:\s+implements\s+([A-Za-z0-9_.,\s<>]+))?\s*\{/g;

  let match;
  const classes = [];

  while ((match = typeRegex.exec(content)) !== null) {
    const rawJavadoc = match[1] || '';
    const rawAnnotations = match[2] || '';
    const kind = match[3];
    const className = match[4];
    const extendsClause = match[5] ? match[5].trim() : null;
    const implementsClause = match[6] ? match[6].trim() : null;

    // Parse class annotations
    const annotations = [];
    const annotRegex = /@([A-Za-z0-9_]+)(?:\(([\s\S]*?)\))?/g;
    let aMatch;
    while ((aMatch = annotRegex.exec(rawAnnotations)) !== null) {
      annotations.push({
        name: `@${aMatch[1]}`,
        value: aMatch[2] ? aMatch[2].trim().replace(/\s+/g, ' ') : null,
      });
    }

    const doc = cleanJavadoc(rawJavadoc);
    const category = categorizeClass(className, packageName, annotations);

    // Extract methods
    const methods = parseMethods(content);

    classes.push({
      id: `${packageName}.${className}`,
      name: className,
      kind,
      package: packageName,
      category,
      filePath: relPath,
      extends: extendsClause,
      implements: implementsClause,
      annotations,
      summary: doc.summary || `${kind} ${className} in ${packageName}`,
      description: doc.description || doc.summary || '',
      tags: doc.tags,
      methods,
      sourceCode: content,
    });
  }

  return { moduleName, classes };
}

function parseMethods(content) {
  const methods = [];
  const methodRegex = /(?:\/\*\*([\s\S]*?)\*\/\s*)?((?:@[A-Za-z0-9_]+(?:\([^)]*\))?\s*)*)(?:public|protected|private)\s+(?:static\s+|final\s+|abstract\s+|synchronized\s+)?([A-Za-z0-9_<>.,\s]+?)\s+([A-Za-z0-9_]+)\s*\(([\s\S]*?)\)(?:\s*throws\s+([A-Za-z0-9_.,\s]+))?\s*(?:\{|;)/g;

  let match;
  while ((match = methodRegex.exec(content)) !== null) {
    const rawJavadoc = match[1] || '';
    const rawAnnotations = match[2] || '';
    const returnType = match[3].trim();
    const methodName = match[4];
    const rawParams = match[5].trim();
    const throwsClause = match[6] ? match[6].trim() : null;

    if (methodName === 'if' || methodName === 'for' || methodName === 'while' || methodName === 'switch') {
      continue;
    }

    // Parse method annotations
    const annotations = [];
    const annotRegex = /@([A-Za-z0-9_]+)(?:\(([\s\S]*?)\))?/g;
    let aMatch;
    while ((aMatch = annotRegex.exec(rawAnnotations)) !== null) {
      annotations.push({
        name: `@${aMatch[1]}`,
        value: aMatch[2] ? aMatch[2].trim().replace(/\s+/g, ' ') : null,
      });
    }

    // Parse parameters
    const params = [];
    if (rawParams.length > 0) {
      const splitParams = rawParams.split(',');
      for (const p of splitParams) {
        const cleanP = p.trim().replace(/@[A-Za-z0-9_]+(?:\([^)]*\))?\s*/g, '');
        const pParts = cleanP.split(/\s+/);
        if (pParts.length >= 2) {
          const pName = pParts[pParts.length - 1];
          const pType = pParts.slice(0, pParts.length - 1).join(' ');
          params.push({ name: pName, type: pType });
        }
      }
    }

    const doc = cleanJavadoc(rawJavadoc);

    // Correlate param docstrings
    const enrichedParams = params.map(p => {
      const found = doc.tags.params.find(tp => tp.name === p.name);
      return {
        name: p.name,
        type: p.type,
        description: found ? found.description : '',
      };
    });

    // Check if HTTP Endpoint
    const httpAnnotation = annotations.find(a => 
      ['@GetMapping', '@PostMapping', '@PutMapping', '@DeleteMapping', '@PatchMapping', '@RequestMapping'].includes(a.name)
    );

    methods.push({
      name: methodName,
      returnType,
      parameters: enrichedParams,
      throws: throwsClause,
      annotations,
      isEndpoint: !!httpAnnotation,
      httpMethod: httpAnnotation ? httpAnnotation.name.replace('@', '').replace('Mapping', '').toUpperCase() : null,
      httpPath: httpAnnotation && httpAnnotation.value ? httpAnnotation.value.replace(/["']/g, '') : null,
      summary: doc.summary || `${methodName} operation`,
      description: doc.description || '',
      returnDescription: doc.tags.returns || '',
      tags: doc.tags,
      signature: `${returnType} ${methodName}(${rawParams.replace(/\s+/g, ' ')})`,
    });
  }

  return methods;
}

function run() {
  const options = parseArgs();
  console.log(`\n🔍 Scanning Java codebase from: ${options.src}`);

  const javaFiles = findJavaFiles(options.src);
  console.log(`📄 Found ${javaFiles.length} Java source files.`);

  const modulesMap = {};

  const moduleMeta = {
    'actor-service': { name: 'Actor Service', port: '8083', desc: 'Actor catalog, filmography, TMDB person sync, Bucket4j rate limiting, and REST endpoints.' },
    'movie-service': { name: 'Movie Service', port: '8082', desc: 'Core movie catalog, recommendations, discovery, TMDB facade, and MongoDB storage.' },
    'user-service': { name: 'User Service', port: '8081', desc: 'User profiles, JWT auth, watchlists, ratings, and account operations.' },
    'ai-service': { name: 'AI Service', port: '8084', desc: 'Semantic movie search, vector embeddings, and Gemini AI integration.' },
    'media-service': { name: 'Media Service', port: '8085', desc: 'Video streaming and MinIO S3 object storage for movie assets.' },
    'api-gateway': { name: 'API Gateway', port: '8080', desc: 'Spring Cloud Gateway routing, token verification, rate limiting, and CORS.' },
    'discovery-service': { name: 'Discovery Service', port: '8761', desc: 'Netflix Eureka service discovery and registry.' },
    'config-service': { name: 'Config Service', port: '8888', desc: 'Spring Cloud Config centralized configuration server.' },
    'shared-library': { name: 'Shared Library', port: 'N/A', desc: 'Common DTOs (ApiResponse, HalResource), exceptions, and security filters.' }
  };

  for (const file of javaFiles) {
    const { moduleName, classes } = parseJavaFile(file, options.src);
    if (!modulesMap[moduleName]) {
      const meta = moduleMeta[moduleName] || { name: moduleName, port: 'N/A', desc: `${moduleName} module` };
      modulesMap[moduleName] = {
        id: moduleName,
        name: meta.name,
        port: meta.port,
        description: meta.desc,
        classes: [],
      };
    }
    modulesMap[moduleName].classes.push(...classes);
  }

  const modules = Object.values(modulesMap);

  const schema = {
    projectName: options.projectName,
    version: options.version,
    description: options.description,
    generatedAt: new Date().toISOString(),
    totalClasses: modules.reduce((acc, m) => acc + m.classes.length, 0),
    modules,
  };

  const outDir = path.dirname(options.out);
  if (!fs.existsSync(outDir)) {
    fs.mkdirSync(outDir, { recursive: true });
  }

  fs.writeFileSync(options.out, JSON.stringify(schema, null, 2));
  console.log(`✅ Documentation schema successfully written to: ${options.out}`);
  console.log(`✨ Total Modules: ${modules.length} | Total Documented Classes: ${schema.totalClasses}\n`);
}

run();

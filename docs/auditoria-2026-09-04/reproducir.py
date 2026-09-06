"""Reproduce observaciones locales con clases compiladas; no conecta servicios externos."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
reports = sorted((root / 'target/surefire-reports').glob('TEST-*.xml'))
if not reports:
    raise SystemExit('Primero ejecuta la suite unitaria para compilar y generar su classpath.')

properties = ET.parse(reports[0]).getroot().findall('./properties/property')
classpath = next(p.get('value') for p in properties if p.get('name') == 'java.class.path')
mockito = next(p for p in classpath.split(':') if '/mockito-core/' in p and p.endswith('.jar'))
source = Path(__file__).with_name('AuditoriaRnf.java')
with tempfile.TemporaryDirectory(prefix='renaser-auditoria-') as output:
    subprocess.run(['javac', '-proc:none', '-cp', classpath, '-d', output, str(source)], check=True)
    subprocess.run(['java', '-javaagent:' + mockito, '-cp', output + ':' + classpath,
                    'AuditoriaRnf'], check=True)

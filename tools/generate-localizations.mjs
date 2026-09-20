// Copy complete, UTF-8 authored language catalogs; never fill missing translations with English.
import fs from 'node:fs';
import path from 'node:path';
const source = path.resolve('tools/lang');
const destination = path.resolve('src/generated/resources/assets/elsebase/lang');
const english = JSON.parse(fs.readFileSync(path.join(source, 'en_us.json'), 'utf8'));
fs.mkdirSync(destination, {recursive: true});
for (const file of fs.readdirSync(source).filter(file => file.endsWith('.json'))) {
    const catalog = JSON.parse(fs.readFileSync(path.join(source, file), 'utf8'));
    if (JSON.stringify(Object.keys(catalog).sort()) !== JSON.stringify(Object.keys(english).sort()))
        throw new Error(`Translation keys differ in ${file}`);
    fs.writeFileSync(path.join(destination, file), JSON.stringify(catalog, null, 2) + '\n', 'utf8');
}

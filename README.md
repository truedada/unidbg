# 编译步骤

## 环境要求

- JDK 8
- Maven 3.9.12

## 本地编译

```bash
mvn -v
```

```bash
mvn -DskipTests package
```

编译完成后，Jar 位于 `target/fqnovel.jar`。

## 运行示例

```bash
java -jar target/fqnovel.jar
```
## docker
```bash
docker run -d --name fqnovel --restart=always -p 9999:9999 gxmandppx/unidbg-fq:latest
```
## 免责声明

**本项目仅供学习交流使用，使用时请遵守相关法律法规。用户需自行承担由此引发的任何法律责任和风险。程序的作者及项目贡献者不对因使用本程序所造成的任何损失、损害或法律后果负责！**
